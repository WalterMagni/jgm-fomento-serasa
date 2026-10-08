package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.service.documento.CompartilhamentoDocumentos;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoAnexoEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoAnexoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Anexos do card, gravados na pasta do cedente no compartilhamento:
 * {@code {cnpj}/liberacao/{numero}/{n}-{nome}}.
 *
 * <p>Qualquer pessoa anexa em qualquer etapa, como comenta: anexo é parte da conversa. Remover é
 * de quem enviou ou de uma analista, e só tira da tela — o arquivo fica no disco.</p>
 */
@Service
@RequiredArgsConstructor
public class LiberacaoAnexoService {

    private final LiberacaoAnexoJpaRepository anexoRepository;
    private final LiberacaoService liberacaoService;
    private final LiberacaoAutorizacao autorizacao;
    private final CompartilhamentoDocumentos compartilhamento;

    @Transactional(readOnly = true)
    public List<LiberacaoAnexoEntity> listar(UUID cardId) {
        return anexoRepository.findByCardIdAndRemovidoEmIsNullOrderByEnviadoEmDesc(cardId);
    }

    @Transactional
    public LiberacaoAnexoEntity anexar(UUID cardId, MultipartFile file, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        String nome = compartilhamento.validar(file);

        long sequencia = anexoRepository.countByCardId(cardId) + 1;
        String relativo = String.join("/", card.getCedenteCnpj(), "liberacao", String.valueOf(card.getNumero()),
                sequencia + "-" + compartilhamento.nomeSanitizado(file));
        compartilhamento.gravar(relativo, file);

        LiberacaoAnexoEntity anexo = anexoRepository.save(LiberacaoAnexoEntity.builder()
                .cardId(cardId)
                .caminhoRelativo(relativo)
                .nomeOriginal(nome)
                .mimeType(file.getContentType())
                .tamanhoBytes(file.getSize())
                .enviadoPorId(autor.getId())
                .enviadoPorNome(autor.getName())
                .enviadoEm(LocalDateTime.now())
                .build());
        liberacaoService.registrarAnexo(card, true, nome, autor);
        return anexo;
    }

    @Transactional(readOnly = true)
    public LiberacaoAnexoEntity buscar(UUID cardId, UUID anexoId) {
        return anexoRepository.findById(anexoId)
                .filter(anexo -> anexo.getCardId().equals(cardId) && anexo.getRemovidoEm() == null)
                .orElseThrow(() -> new EntityNotFoundException("Anexo não encontrado"));
    }

    @Transactional(readOnly = true)
    public byte[] conteudo(LiberacaoAnexoEntity anexo) {
        return compartilhamento.ler(anexo.getCaminhoRelativo());
    }

    @Transactional
    public void remover(UUID cardId, UUID anexoId, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        LiberacaoAnexoEntity anexo = buscar(cardId, anexoId);
        if (!autor.getId().equals(anexo.getEnviadoPorId()) && !autor.isAnalista()) {
            throw new AcessoNegadoException("Só " + anexo.getEnviadoPorNome() + " ou uma analista remove este anexo.");
        }
        anexo.setRemovidoEm(LocalDateTime.now());
        anexo.setRemovidoPorNome(autor.getName());
        anexoRepository.save(anexo);
        liberacaoService.registrarAnexo(card, false, anexo.getNomeOriginal(), autor);
    }
}
