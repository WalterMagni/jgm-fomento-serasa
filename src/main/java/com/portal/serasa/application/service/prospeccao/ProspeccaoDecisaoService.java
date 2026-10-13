package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.CondicaoSugerida;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.RespostaCliente;
import com.portal.serasa.domain.model.prospeccao.ResultadoDecisao;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoAceiteEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDecisaoCondicaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDecisaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoAceiteJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDecisaoCondicaoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDecisaoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Decisão da analista e resposta do cliente — o trecho do fluxo que veio do Portal Comercial.
 *
 * <p>A decisão sai da análise: aprova com limite e condições por produto — o card vai para Aprovado
 * e o comercial junta os documentos — ou reprova com motivo. O comitê pode ajustar a proposta ao
 * aprovar. A resposta do cliente sai do aceite, depois do comitê: aceita e segue para o contrato,
 * recusa e encerra, ou faz contraproposta e volta ao comitê numa rodada nova. Cada decisão é uma
 * linha; a nova tira a vigência da anterior sem apagá-la.</p>
 */
@Service
@RequiredArgsConstructor
public class ProspeccaoDecisaoService {

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ProspeccaoDecisaoJpaRepository decisaoRepository;
    private final ProspeccaoDecisaoCondicaoJpaRepository condicaoRepository;
    private final ProspeccaoAceiteJpaRepository aceiteRepository;
    private final org.springframework.context.ApplicationEventPublisher acontecimentos;
    private final ProspeccaoRodadaComite rodadaComite;

    /** Condição pedida na decisão: produto do catálogo e valor. */
    public record Condicao(String produto, BigDecimal valor) {
    }

    /** @param ressalva texto da analista quando aprova com ressalva; ignorado ao reprovar */
    public record ComandoDecisao(ResultadoDecisao resultado, BigDecimal limite, List<Condicao> condicoes,
                                 String parecer, MotivoRecusa motivoRecusa, String observacao, String ressalva) {
    }

    public record ComandoAceite(RespostaCliente resposta, String mensagem, BigDecimal limiteSugerido,
                                List<CondicaoSugerida> condicoesSugeridas) {
    }

    /** Decisão com as condições já carregadas, como a tela mostra. */
    public record DecisaoCompleta(ProspeccaoDecisaoEntity decisao, List<ProspeccaoDecisaoCondicaoEntity> condicoes) {
    }

    // ---------------------------------------------------------------- decisão

    @Transactional
    public DecisaoCompleta decidir(UUID id, ComandoDecisao comando, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        if (card.getEstagio() != EstagioProspeccao.EM_ANALISE) {
            throw new TransicaoInvalidaException("Só se decide um card em análise.");
        }
        autorizacao.exigirDona(card, autor);
        if (comando.resultado() == null) {
            throw new IllegalArgumentException("Informe se aprova ou reprova.");
        }

        boolean aprova = comando.resultado() == ResultadoDecisao.APROVADO;
        List<Condicao> condicoes = aprova ? validarAprovacao(comando) : List.of();
        String parecer = limpar(comando.parecer());

        // A transição valida o motivo da reprovação; ela roda antes de gravar a decisão para não
        // sobrar decisão órfã de um card que não se moveu.
        prospeccaoService.mover(id,
                aprova ? EstagioProspeccao.APROVADO : EstagioProspeccao.REPROVADO,
                aprova ? null : comando.motivoRecusa(),
                aprova ? null : comando.observacao(),
                autor);

        DecisaoCompleta completa = gravar(id, comando, condicoes, parecer, autor);
        String resumo = resumo(completa.decisao(), completa.condicoes());
        ProspeccaoEntity atualizado = prospeccaoService.buscar(id);
        prospeccaoService.registrarEvento(atualizado, TipoEventoProspeccao.DECISAO, null, null, null, resumo, autor);
        if (aprova) {
            acontecimentos.publishEvent(new ProspeccaoAcontecimento.AprovadoNaAnalise(atualizado, autor, resumo));
        }
        return completa;
    }

    /**
     * O comitê aprovou com outro limite ou outras condições. Vira a decisão vigente — é a ela que
     * o cliente responde no aceite. O card não muda de estágio aqui: quem move é o resultado.
     */
    @Transactional
    public DecisaoCompleta ajustarPeloComite(UUID id, BigDecimal limite, List<Condicao> condicoes,
                                             String parecer, UserEntity autor) {
        // A ressalva é da análise: o ajuste do comitê muda limite e condições, não a ressalva.
        String ressalva = decisaoRepository.findByProspeccaoIdAndVigenteTrue(id)
                .map(ProspeccaoDecisaoEntity::getRessalva).orElse(null);
        ComandoDecisao comando = new ComandoDecisao(ResultadoDecisao.APROVADO, limite, condicoes, parecer, null, null, ressalva);
        List<Condicao> validas = validarAprovacao(comando);
        DecisaoCompleta completa = gravar(id, comando, validas, limpar(parecer), autor);
        prospeccaoService.registrarEvento(prospeccaoService.buscar(id), TipoEventoProspeccao.DECISAO, null, null, null,
                "Comitê ajustou a proposta: " + resumo(completa.decisao(), completa.condicoes()), autor);
        return completa;
    }

    /** Grava a decisão nova como vigente, com as condições na ordem em que vieram. */
    private DecisaoCompleta gravar(UUID id, ComandoDecisao comando, List<Condicao> condicoes, String parecer,
                                   UserEntity autor) {
        boolean aprova = comando.resultado() == ResultadoDecisao.APROVADO;
        decisaoRepository.encerrarVigencia(id);
        ProspeccaoDecisaoEntity decisao = decisaoRepository.save(ProspeccaoDecisaoEntity.builder()
                .prospeccaoId(id)
                .versao(decisaoRepository.ultimaVersao(id) + 1)
                .resultado(comando.resultado())
                .limite(aprova ? comando.limite() : null)
                .parecer(parecer)
                .ressalva(aprova ? limpar(comando.ressalva()) : null)
                .motivoRecusa(aprova ? null : comando.motivoRecusa())
                .vigente(true)
                .decididoPor(autor.getId())
                .decididoPorNome(autor.getName())
                .decididoEm(LocalDateTime.now())
                .build());

        List<ProspeccaoDecisaoCondicaoEntity> salvas = new ArrayList<>();
        for (int i = 0; i < condicoes.size(); i++) {
            Condicao condicao = condicoes.get(i);
            salvas.add(condicaoRepository.save(ProspeccaoDecisaoCondicaoEntity.builder()
                    .decisaoId(decisao.getId())
                    .produto(condicao.produto().trim())
                    .valor(condicao.valor())
                    .ordem(i)
                    .build()));
        }

        return new DecisaoCompleta(decisao, salvas);
    }

    /**
     * Aprovação exige limite e ao menos uma condição, como no Portal Comercial. A soma das
     * condições pode passar do limite — o limite é teto informativo e a tela só avisa.
     */
    private List<Condicao> validarAprovacao(ComandoDecisao comando) {
        if (comando.limite() == null || comando.limite().signum() <= 0) {
            throw new IllegalArgumentException("Aprovação exige limite maior que zero.");
        }
        List<Condicao> condicoes = comando.condicoes() == null ? List.of() : comando.condicoes();
        if (condicoes.isEmpty()) {
            throw new IllegalArgumentException("Aprovação exige ao menos uma condição (produto e valor).");
        }
        for (Condicao condicao : condicoes) {
            if (condicao.produto() == null || condicao.produto().isBlank()) {
                throw new IllegalArgumentException("Toda condição precisa de produto.");
            }
            if (condicao.valor() == null || condicao.valor().signum() <= 0) {
                throw new IllegalArgumentException("Valor da condição de " + condicao.produto() + " precisa ser maior que zero.");
            }
        }
        return condicoes;
    }

    // ---------------------------------------------------------------- aceite

    /**
     * Registra a resposta do cliente à decisão vigente.
     *
     * <p>Na Fase A quem registra é a equipe, em nome do comercial. A marca fica gravada para que,
     * quando o comercial tiver acesso, dê para distinguir o que ele mesmo respondeu.</p>
     */
    @Transactional
    public ProspeccaoAceiteEntity registrarAceite(UUID id, ComandoAceite comando, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        if (card.getEstagio() != EstagioProspeccao.AGUARDANDO_ACEITE) {
            throw new TransicaoInvalidaException("O card não está aguardando a resposta do cliente.");
        }
        autorizacao.exigirEscrita(card, autor);
        if (comando.resposta() == null) {
            throw new IllegalArgumentException("Informe a resposta do cliente.");
        }
        ProspeccaoDecisaoEntity vigente = decisaoRepository.findByProspeccaoIdAndVigenteTrue(id)
                .filter(decisao -> decisao.getResultado() == ResultadoDecisao.APROVADO)
                .orElseThrow(() -> new TransicaoInvalidaException(
                        "Não há proposta aprovada para o cliente responder. Volte o card para a análise e decida."));

        String mensagem = limpar(comando.mensagem());
        if (comando.resposta() == RespostaCliente.CONTRAPROPOSTA && mensagem == null) {
            throw new IllegalArgumentException("Contraproposta exige a mensagem do cliente.");
        }
        boolean contraproposta = comando.resposta() == RespostaCliente.CONTRAPROPOSTA;
        List<CondicaoSugerida> sugeridas = contraproposta && comando.condicoesSugeridas() != null
                ? comando.condicoesSugeridas().stream()
                        .filter(c -> c.produto() != null && !c.produto().isBlank() && c.valor() != null && c.valor().signum() > 0)
                        .toList()
                : null;

        switch (comando.resposta()) {
            case ACEITO -> prospeccaoService.mover(id, EstagioProspeccao.CONTRATO, null, null, autor);
            case RECUSADO -> prospeccaoService.mover(id, EstagioProspeccao.RECUSADO_CLIENTE, null, mensagem, autor);
            // O comitê reavalia o que o cliente pediu; documentos já conferidos não se repetem.
            case CONTRAPROPOSTA -> rodadaComite.abrir(id, "contraproposta do cliente", autor);
        }

        ProspeccaoAceiteEntity aceite = aceiteRepository.save(ProspeccaoAceiteEntity.builder()
                .prospeccaoId(id)
                .decisaoId(vigente.getId())
                .resposta(comando.resposta())
                .limiteSugerido(contraproposta ? positivoOuNulo(comando.limiteSugerido()) : null)
                .condicoesSugeridas(sugeridas == null || sugeridas.isEmpty() ? null : sugeridas)
                .mensagem(mensagem)
                .registradoPor(autor.getId())
                .registradoPorNome(autor.getName())
                .emNomeDoComercial(emNomeDoComercial(card, autor))
                .registradoEm(LocalDateTime.now())
                .build());

        ProspeccaoEntity atualizado = prospeccaoService.buscar(id);
        prospeccaoService.registrarEvento(atualizado, TipoEventoProspeccao.ACEITE, null, null, null,
                resumo(aceite), autor);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.RespostaCliente(atualizado, aceite.getResposta(), autor,
                aceite.getMensagem()));
        return aceite;
    }

    /**
     * Quem registrou não é o comercial do card. Cliente direto não tem comercial, e aí a resposta
     * é da própria equipe — marcar "em nome do comercial" seria falso.
     */
    static boolean emNomeDoComercial(ProspeccaoEntity card, UserEntity autor) {
        boolean temComercial = card.getComercialId() != null
                || (card.getComercialNome() != null && !card.getComercialNome().isBlank());
        return temComercial && !autor.getId().equals(card.getComercialId());
    }

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public List<DecisaoCompleta> historico(UUID id) {
        List<ProspeccaoDecisaoEntity> decisoes = decisaoRepository.findByProspeccaoIdOrderByVersaoDesc(id);
        Map<UUID, List<ProspeccaoDecisaoCondicaoEntity>> condicoes = condicoesPorDecisao(
                decisoes.stream().map(ProspeccaoDecisaoEntity::getId).toList());
        return decisoes.stream()
                .map(decisao -> new DecisaoCompleta(decisao, condicoes.getOrDefault(decisao.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProspeccaoAceiteEntity> aceites(UUID id) {
        return aceiteRepository.findByProspeccaoIdOrderByRegistradoEmDesc(id);
    }

    /** Ressalva da decisão vigente de cada card; só aparecem os que têm. */
    @Transactional(readOnly = true)
    public Map<UUID, String> ressalvasVigentes(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return decisaoRepository.findByProspeccaoIdInAndVigenteTrue(cardIds).stream()
                .filter(decisao -> decisao.getRessalva() != null)
                .collect(Collectors.toMap(ProspeccaoDecisaoEntity::getProspeccaoId, ProspeccaoDecisaoEntity::getRessalva));
    }

    /** Limite vigente de cada card, numa consulta só, para o selo no quadro. */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> limitesVigentes(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return decisaoRepository.findByProspeccaoIdInAndVigenteTrue(cardIds).stream()
                .filter(decisao -> decisao.getLimite() != null)
                .collect(Collectors.toMap(ProspeccaoDecisaoEntity::getProspeccaoId, ProspeccaoDecisaoEntity::getLimite));
    }

    /** Decisão vigente de cada card, com condições, numa consulta só — para a exportação. */
    @Transactional(readOnly = true)
    public Map<UUID, DecisaoCompleta> vigentes(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        List<ProspeccaoDecisaoEntity> decisoes = decisaoRepository.findByProspeccaoIdInAndVigenteTrue(cardIds);
        Map<UUID, List<ProspeccaoDecisaoCondicaoEntity>> condicoes = condicoesPorDecisao(
                decisoes.stream().map(ProspeccaoDecisaoEntity::getId).toList());
        return decisoes.stream().collect(Collectors.toMap(ProspeccaoDecisaoEntity::getProspeccaoId,
                decisao -> new DecisaoCompleta(decisao, condicoes.getOrDefault(decisao.getId(), List.of()))));
    }

    /** Última resposta do cliente de cada card. */
    @Transactional(readOnly = true)
    public Map<UUID, ProspeccaoAceiteEntity> ultimasRespostas(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ProspeccaoAceiteEntity> ultimas = new java.util.HashMap<>();
        aceiteRepository.findByProspeccaoIdInOrderByRegistradoEmDesc(cardIds)
                .forEach(aceite -> ultimas.putIfAbsent(aceite.getProspeccaoId(), aceite));
        return ultimas;
    }

    private Map<UUID, List<ProspeccaoDecisaoCondicaoEntity>> condicoesPorDecisao(List<UUID> decisaoIds) {
        if (decisaoIds.isEmpty()) {
            return Map.of();
        }
        return condicaoRepository.findByDecisaoIdInOrderByOrdemAsc(decisaoIds).stream()
                .collect(Collectors.groupingBy(ProspeccaoDecisaoCondicaoEntity::getDecisaoId));
    }

    // ---------------------------------------------------------------- textos

    private static final NumberFormat REAIS = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    static String reais(BigDecimal valor) {
        synchronized (REAIS) {
            return REAIS.format(valor);
        }
    }

    /** Linha da timeline: o que foi decidido, legível sem abrir a aba de decisão. */
    static String resumo(ProspeccaoDecisaoEntity decisao, List<ProspeccaoDecisaoCondicaoEntity> condicoes) {
        if (decisao.getResultado() == ResultadoDecisao.REPROVADO) {
            return "Reprovado" + (decisao.getParecer() != null ? ": " + decisao.getParecer() : "");
        }
        String produtos = condicoes.stream()
                .map(c -> c.getProduto() + " " + reais(c.getValor()))
                .collect(Collectors.joining(", "));
        return (decisao.getRessalva() != null ? "Aprovado com ressalva, limite de " : "Aprovado com limite de ")
                + reais(decisao.getLimite()) + " — " + produtos
                + (decisao.getVersao() > 1 ? " (decisão " + decisao.getVersao() + ")" : "")
                + (decisao.getRessalva() != null ? ". Ressalva: " + decisao.getRessalva() : "");
    }

    static String resumo(ProspeccaoAceiteEntity aceite) {
        String quem = Boolean.TRUE.equals(aceite.getEmNomeDoComercial()) ? " (registrado em nome do comercial)" : "";
        return switch (aceite.getResposta()) {
            case ACEITO -> "Cliente aceitou a proposta" + quem;
            case RECUSADO -> "Cliente recusou a proposta" + (aceite.getMensagem() != null ? ": " + aceite.getMensagem() : "") + quem;
            case CONTRAPROPOSTA -> "Contraproposta do cliente: " + aceite.getMensagem()
                    + (aceite.getLimiteSugerido() != null ? " — pede limite de " + reais(aceite.getLimiteSugerido()) : "") + quem;
        };
    }

    private static String limpar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static BigDecimal positivoOuNulo(BigDecimal valor) {
        return valor != null && valor.signum() > 0 ? valor : null;
    }
}
