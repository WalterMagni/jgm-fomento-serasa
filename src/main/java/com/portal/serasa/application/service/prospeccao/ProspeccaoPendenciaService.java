package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.liberacao.LiberacaoService;
import com.portal.serasa.application.service.liberacao.MencaoParser;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.StatusPendencia;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoPendenciaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pendências: o que a analista pede ao comercial — informação, dados comerciais, documentos.
 *
 * <p>Enquanto houver pendência aberta o prazo do estágio para, e o card mostra "aguardando
 * comercial". Responder fecha a pendência; quando fecha a última, o relógio volta a correr. Na
 * Fase A quem responde é a equipe, com o que o comercial trouxe.</p>
 */
@Service
@RequiredArgsConstructor
public class ProspeccaoPendenciaService {

    static final int LIMITE_TEXTO = 8000;

    /** Campos de dados comerciais que podem ser exigidos — os nomes do DTO. */
    static final Set<String> CAMPOS_COMERCIAIS = Set.of(
            "faturamentoMensal", "ticketMedio", "valorMedioOperacoes",
            "prazoMedioVendas", "prazoMedioPgtoFornecedor", "prazoMedioEntrega", "possuiCanhotosDias",
            "operaMercadoriaEntregue", "operaMercadoriaEmbarcada",
            "regiaoAtendimento", "produtos", "parceirosAtuais", "outros");

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ProspeccaoPendenciaJpaRepository pendenciaRepository;
    private final ProspeccaoDocumentoJpaRepository documentoRepository;
    private final ApplicationEventPublisher acontecimentos;

    @Transactional
    public ProspeccaoPendenciaEntity abrir(UUID cardId, String pergunta, List<String> campos, List<UUID> documentos,
                                           UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(cardId);
        if (card.getEstagio().terminal()) {
            throw new TransicaoInvalidaException("Card encerrado: reabra antes de abrir pendência.");
        }
        autorizacao.exigirDona(card, autor);
        String texto = validar(pergunta, "Escreva o que está sendo pedido.");

        List<String> camposValidos = campos == null ? List.of() : campos.stream().distinct().toList();
        camposValidos.stream()
                .filter(campo -> !CAMPOS_COMERCIAIS.contains(campo))
                .findFirst()
                .ifPresent(campo -> {
                    throw new IllegalArgumentException("Campo comercial desconhecido: " + campo);
                });

        List<UUID> documentosValidos = documentos == null ? List.of() : documentos.stream().distinct().toList();
        if (!documentosValidos.isEmpty()) {
            Set<UUID> doCard = documentoRepository.findByProspeccaoId(cardId).stream()
                    .map(ProspeccaoDocumentoEntity::getId)
                    .collect(Collectors.toSet());
            if (!doCard.containsAll(documentosValidos)) {
                throw new IllegalArgumentException("Documento pedido não pertence a este card.");
            }
        }

        ProspeccaoPendenciaEntity pendencia = pendenciaRepository.save(ProspeccaoPendenciaEntity.builder()
                .prospeccaoId(cardId)
                .pergunta(texto)
                .camposExigidos(camposValidos.isEmpty() ? null : camposValidos)
                .documentosPedidos(documentosValidos.isEmpty() ? null : documentosValidos)
                .status(StatusPendencia.ABERTA)
                .abertaPor(autor.getId())
                .abertaPorNome(autor.getName())
                .abertaEm(LocalDateTime.now())
                .build());

        prospeccaoService.pausar(card);
        prospeccaoService.registrarEvento(card, TipoEventoProspeccao.PENDENCIA_ABERTA, null, null, null,
                MencaoParser.textoPlano(texto), autor);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.PendenciaAberta(card, pendencia, autor,
                MencaoParser.usuarios(texto), LiberacaoService.trecho(texto)));
        return pendencia;
    }

    @Transactional
    public ProspeccaoPendenciaEntity responder(UUID pendenciaId, String resposta, UserEntity autor) {
        ProspeccaoPendenciaEntity pendencia = aberta(pendenciaId);
        ProspeccaoEntity card = prospeccaoService.buscar(pendencia.getProspeccaoId());
        autorizacao.exigirEscrita(card, autor);

        String texto = validar(resposta, "Escreva a resposta.");
        pendencia.setStatus(StatusPendencia.RESPONDIDA);
        pendencia.setResposta(texto);
        pendencia.setRespondidaPor(autor.getId());
        pendencia.setRespondidaPorNome(autor.getName());
        pendencia.setEmNomeDoComercial(ProspeccaoDecisaoService.emNomeDoComercial(card, autor));
        pendencia.setFechadaEm(LocalDateTime.now());
        pendenciaRepository.save(pendencia);

        retomarSeFoiAUltima(card);
        prospeccaoService.registrarEvento(card, TipoEventoProspeccao.PENDENCIA_RESPONDIDA, null, null, null,
                MencaoParser.textoPlano(texto), autor);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.PendenciaRespondida(card, pendencia, autor,
                LiberacaoService.trecho(texto)));
        return pendencia;
    }

    /** Desiste do pedido. Só quem abriu ou a dona do card. */
    @Transactional
    public ProspeccaoPendenciaEntity cancelar(UUID pendenciaId, UserEntity autor) {
        ProspeccaoPendenciaEntity pendencia = aberta(pendenciaId);
        ProspeccaoEntity card = prospeccaoService.buscar(pendencia.getProspeccaoId());
        boolean abriu = autor != null && autor.getId() != null && autor.getId().equals(pendencia.getAbertaPor());
        if (!abriu && !autorizacao.ehDona(card, autor)) {
            throw new AcessoNegadoException("Só quem abriu a pendência ou a dona do card cancela.");
        }
        pendencia.setStatus(StatusPendencia.CANCELADA);
        pendencia.setFechadaEm(LocalDateTime.now());
        pendenciaRepository.save(pendencia);

        retomarSeFoiAUltima(card);
        prospeccaoService.registrarEvento(card, TipoEventoProspeccao.PENDENCIA_CANCELADA, null, null, null,
                MencaoParser.textoPlano(pendencia.getPergunta()), autor);
        return pendencia;
    }

    @Transactional(readOnly = true)
    public List<ProspeccaoPendenciaEntity> listar(UUID cardId) {
        return pendenciaRepository.findByProspeccaoIdOrderByAbertaEmDesc(cardId);
    }

    /** Pendências abertas por card, para o selo do quadro. Card sem pendência não aparece no mapa. */
    @Transactional(readOnly = true)
    public Map<UUID, Long> abertasPorCard(Collection<UUID> cardIds) {
        Map<UUID, Long> mapa = new HashMap<>();
        if (cardIds.isEmpty()) {
            return mapa;
        }
        for (Object[] linha : pendenciaRepository.contarAbertas(cardIds)) {
            mapa.put((UUID) linha[0], (Long) linha[1]);
        }
        return mapa;
    }

    private void retomarSeFoiAUltima(ProspeccaoEntity card) {
        if (pendenciaRepository.countByProspeccaoIdAndStatus(card.getId(), StatusPendencia.ABERTA) == 0) {
            prospeccaoService.retomar(card);
        }
    }

    private ProspeccaoPendenciaEntity aberta(UUID pendenciaId) {
        ProspeccaoPendenciaEntity pendencia = pendenciaRepository.findById(pendenciaId)
                .orElseThrow(() -> new EntityNotFoundException("Pendência não encontrada"));
        if (pendencia.getStatus() != StatusPendencia.ABERTA) {
            throw new TransicaoInvalidaException("Esta pendência já foi " + pendencia.getStatus().name().toLowerCase() + ".");
        }
        return pendencia;
    }

    private static String validar(String texto, String vazio) {
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException(vazio);
        }
        if (limpo.length() > LIMITE_TEXTO) {
            throw new IllegalArgumentException("Texto passa de " + LIMITE_TEXTO + " caracteres.");
        }
        return limpo;
    }
}
