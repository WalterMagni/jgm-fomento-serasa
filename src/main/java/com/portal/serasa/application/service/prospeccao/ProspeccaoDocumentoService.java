package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.prospeccao.StatusDocumento;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Conferência do checklist: validar, rejeitar, dispensar, e o ciclo de vida do sócio. */
@Service
@RequiredArgsConstructor
public class ProspeccaoDocumentoService {

    private final ProspeccaoDocumentoJpaRepository documentoRepository;
    private final ProspeccaoService prospeccaoService;

    @Transactional(readOnly = true)
    public List<ProspeccaoDocumentoEntity> listar(UUID prospeccaoId) {
        return documentoRepository
                .findByProspeccaoIdOrderByEscopoAscSocioNomeAscNomeSnapshotAsc(prospeccaoId);
    }

    /** Checklist de vários cards, agrupado por card. Evita N+1 na listagem do kanban. */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, List<ProspeccaoDocumentoEntity>> listarPorCards(
            java.util.Collection<UUID> prospeccaoIds) {
        if (prospeccaoIds.isEmpty()) {
            return java.util.Map.of();
        }
        return documentoRepository.findByProspeccaoIdIn(prospeccaoIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ProspeccaoDocumentoEntity::getProspeccaoId));
    }

    /**
     * Muda o status de um item do checklist.
     *
     * <p>A observação é gravada junto porque é ela que carrega o dado real — na planilha o campo
     * traz "OK - CRC válido", "OK - 7 Clientes", "Válida até 2032". Status estruturado sem o
     * texto ao lado perderia metade da informação.</p>
     */
    @Transactional
    public ProspeccaoDocumentoEntity atualizarStatus(UUID documentoId, StatusDocumento status,
                                                     String observacao, String motivo, UserEntity autor) {
        ProspeccaoDocumentoEntity documento = buscar(documentoId);
        if (status.exigeMotivo() && (motivo == null || motivo.isBlank())) {
            throw new IllegalArgumentException("Motivo é obrigatório para " + status + ".");
        }

        documento.setStatus(status);
        documento.setMotivo(status.exigeMotivo() ? motivo : null);
        if (observacao != null) {
            documento.setObservacao(observacao);
        }
        if (status == StatusDocumento.VALIDADO) {
            documento.setValidadoEm(LocalDateTime.now());
            documento.setValidadoPor(autor != null ? autor.getId() : null);
        } else {
            // Rejeitar ou dispensar desfaz a validação anterior: o registro não pode dizer que o
            // documento foi aceito quando ele acabou de ser recusado.
            documento.setValidadoEm(null);
            documento.setValidadoPor(null);
        }
        documento.setAtualizadoEm(LocalDateTime.now());
        documentoRepository.save(documento);

        prospeccaoService.registrarEvento(
                prospeccaoService.buscar(documento.getProspeccaoId()),
                eventoDe(status), null, null, null,
                documento.getNomeSnapshot() + (motivo == null || motivo.isBlank() ? "" : " — " + motivo),
                autor);
        return documento;
    }

    /**
     * Marca um sócio como fora do checklist, sem apagá-lo.
     *
     * <p>A planilha registra "vai sair da sociedade" e "Faleceu — recebemos certidão de óbito".
     * Some sozinho seria perda de rastro; inativo para de travar o fechamento e continua na tela.
     */
    @Transactional
    public int definirSocioAtivo(UUID prospeccaoId, String socioNome, boolean ativo,
                                 String motivo, UserEntity autor) {
        if (!ativo && (motivo == null || motivo.isBlank())) {
            throw new IllegalArgumentException("Informe por que este sócio saiu do checklist.");
        }
        List<ProspeccaoDocumentoEntity> itens = documentoRepository.findByProspeccaoId(prospeccaoId).stream()
                .filter(item -> socioNome != null && socioNome.equals(item.getSocioNome()))
                .toList();
        if (itens.isEmpty()) {
            throw new EntityNotFoundException("Sócio não encontrado no checklist: " + socioNome);
        }

        itens.forEach(item -> {
            item.setSocioAtivo(ativo);
            item.setSocioInativoMotivo(ativo ? null : motivo);
            item.setAtualizadoEm(LocalDateTime.now());
        });
        documentoRepository.saveAll(itens);

        prospeccaoService.registrarEvento(prospeccaoService.buscar(prospeccaoId),
                TipoEventoProspeccao.NOTA, null, null, null,
                ativo ? "Sócio %s reativado no checklist".formatted(socioNome)
                      : "Sócio %s fora do checklist: %s".formatted(socioNome, motivo),
                autor);
        return itens.size();
    }

    private TipoEventoProspeccao eventoDe(StatusDocumento status) {
        return switch (status) {
            case VALIDADO -> TipoEventoProspeccao.DOC_VALIDADO;
            case REJEITADO -> TipoEventoProspeccao.DOC_REJEITADO;
            case DISPENSADO, NAO_APLICAVEL -> TipoEventoProspeccao.DOC_DISPENSADO;
            case RECEBIDO -> TipoEventoProspeccao.DOC_RECEBIDO;
            case PENDENTE -> TipoEventoProspeccao.NOTA;
        };
    }

    private ProspeccaoDocumentoEntity buscar(UUID documentoId) {
        return documentoRepository.findById(documentoId)
                .orElseThrow(() -> new EntityNotFoundException("Item do checklist não encontrado"));
    }
}
