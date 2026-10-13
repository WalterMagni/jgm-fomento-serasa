package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.liberacao.LiberacaoService;
import com.portal.serasa.application.service.liberacao.MencaoParser;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.MotivoRecusa;
import com.portal.serasa.domain.model.prospeccao.ResultadoComite;
import com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoParecerJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comitê e contrato — o fim da esteira.
 *
 * <p>Mesmas regras da esteira de liberação: entrar no comitê abre uma rodada e convoca cada membro
 * ({@code users.comite}) com um parecer vazio; só o convocado registra, e pode rever; sair do
 * comitê exige ao menos um parecer registrado. Quem registra o resultado é a analista dona ou
 * um membro do comitê. Aprovado, o card vai aguardar o aceite do cliente, que pode voltar ao
 * comitê com contraproposta.</p>
 *
 * <p>O contrato é o último passo: a equipe anota quem assina pelo cliente, e a dona conclui o card
 * em HABILITADO — dali em diante o cedente vive na esteira de liberação.</p>
 */
@Service
@RequiredArgsConstructor
public class ProspeccaoComiteService {

    static final int LIMITE_TEXTO = 8000;

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ProspeccaoJpaRepository prospeccaoRepository;
    private final ProspeccaoParecerJpaRepository parecerRepository;
    private final ApplicationEventPublisher acontecimentos;
    private final ProspeccaoRodadaComite rodadaComite;
    private final ProspeccaoDecisaoService decisaoService;

    // ------------------------------------------------------------------ envio

    /**
     * Leva o card ao comitê — da documentação, ou de volta do contrato — numa rodada nova.
     *
     * <p>A passagem da documentação continua exigindo todos os obrigatórios resolvidos; quem
     * confere é a troca de estágio, na mesma transação.</p>
     */
    @Transactional
    public ProspeccaoEntity enviar(UUID id, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        if (!card.getEstagio().aceita(EstagioProspeccao.COMITE)) {
            throw new TransicaoInvalidaException("O card não pode ir ao comitê a partir de " + card.getEstagio() + ".");
        }
        autorizacao.exigirDona(card, autor);
        return rodadaComite.abrir(id, null, autor);
    }

    // ------------------------------------------------------------------ parecer

    public record ParecerRegistrado(ProspeccaoParecerEntity parecer, boolean ultimo) {
    }

    @Transactional
    public ParecerRegistrado registrarParecer(UUID id, PosicaoParecer posicao, String texto, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        if (card.getEstagio() != EstagioProspeccao.COMITE) {
            throw new TransicaoInvalidaException("Parecer só é registrado com o card no comitê.");
        }
        if (posicao == null) {
            throw new IllegalArgumentException("Escolha a posição do parecer.");
        }
        if (autor == null || autor.getId() == null) {
            throw new AcessoNegadoException("Não autenticado");
        }
        ProspeccaoParecerEntity parecer = parecerRepository
                .findByProspeccaoIdAndRodadaAndUsuarioId(id, card.getRodadaComite(), autor.getId())
                .orElseThrow(() -> new AcessoNegadoException("Seu parecer não é esperado neste card."));

        String limpo = texto == null || texto.isBlank() ? null : texto.trim();
        if (limpo != null && limpo.length() > LIMITE_TEXTO) {
            throw new IllegalArgumentException("Parecer passa de " + LIMITE_TEXTO + " caracteres.");
        }
        boolean revisao = parecer.registrado();
        String anterior = parecer.getTexto();
        parecer.setPosicao(posicao);
        parecer.setTexto(limpo);
        parecer.setRegistradoEm(LocalDateTime.now());
        parecer.setUsuarioNome(autor.getName());
        ProspeccaoParecerEntity salvo = parecerRepository.save(parecer);

        prospeccaoService.registrarEvento(card, TipoEventoProspeccao.PARECER, null, null, null,
                (revisao ? "Parecer revisto: " : "Parecer: ") + rotulo(posicao)
                        + (limpo == null ? "" : " — " + MencaoParser.textoPlano(limpo)),
                autor);

        boolean ultimo = !revisao && aguardando(rodada(card)).isEmpty();
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.ParecerDado(card, salvo, ultimo, revisao, autor,
                MencaoParser.novosUsuarios(anterior, limpo), LiberacaoService.trecho(limpo)));
        return new ParecerRegistrado(salvo, ultimo);
    }

    // ---------------------------------------------------------------- resultado

    /** Limite e condições que o comitê aprovou no lugar dos da análise. */
    public record AjusteProposta(java.math.BigDecimal limite, List<ProspeccaoDecisaoService.Condicao> condicoes) {
    }

    /**
     * O resultado do comitê. Exige ao menos um parecer registrado na rodada; quem não deu parecer
     * fica dito no histórico, e o parecer vazio some.
     */
    @Transactional
    public ProspeccaoEntity registrarResultado(UUID id, ResultadoComite resultado, MotivoRecusa motivo,
                                               String observacao, UserEntity autor) {
        return registrarResultado(id, resultado, motivo, observacao, null, autor);
    }

    /**
     * Resultado com a proposta ajustada pelo comitê. Aprovar sem ajuste mantém o limite e as
     * condições da análise; com ajuste, a proposta nova passa a ser a vigente, e é a ela que o
     * cliente responde no aceite.
     */
    @Transactional
    public ProspeccaoEntity registrarResultado(UUID id, ResultadoComite resultado, MotivoRecusa motivo,
                                               String observacao, AjusteProposta ajuste, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        if (card.getEstagio() != EstagioProspeccao.COMITE) {
            throw new TransicaoInvalidaException("O card não está no comitê.");
        }
        if (!autorizacao.ehDona(card, autor) && (autor == null || !autor.isComite())) {
            throw new AcessoNegadoException("Só a analista dona do card ou um membro do comitê registra o resultado.");
        }
        if (resultado == null) {
            throw new IllegalArgumentException("Informe o resultado do comitê.");
        }
        List<ProspeccaoParecerEntity> rodada = rodada(card);
        if (rodada.stream().noneMatch(ProspeccaoParecerEntity::registrado)) {
            throw new TransicaoInvalidaException("O comitê ainda não deu nenhum parecer nesta rodada.");
        }
        List<String> faltando = aguardando(rodada);
        List<UUID> membros = rodada.stream().map(ProspeccaoParecerEntity::getUsuarioId).filter(Objects::nonNull).toList();

        EstagioProspeccao destino = switch (resultado) {
            case APROVADO -> EstagioProspeccao.AGUARDANDO_ACEITE;
            case DEVOLVIDO -> EstagioProspeccao.EM_ANALISE;
            case REPROVADO -> EstagioProspeccao.REPROVADO;
        };
        String texto = observacao == null || observacao.isBlank() ? null : observacao.trim();
        ProspeccaoEntity movido = prospeccaoService.mover(id, destino,
                resultado == ResultadoComite.REPROVADO ? motivo : null, texto, autor);
        parecerRepository.apagarAguardando(id, card.getRodadaComite());
        if (resultado == ResultadoComite.APROVADO && ajuste != null && ajuste.limite() != null) {
            decisaoService.ajustarPeloComite(id, ajuste.limite(), ajuste.condicoes(), texto, autor);
        }

        String rotulo = switch (resultado) {
            case APROVADO -> "Aprovado no comitê";
            case DEVOLVIDO -> "Comitê devolveu para a análise";
            case REPROVADO -> "Reprovado no comitê";
        };
        String historico = rotulo + (texto == null ? "" : ": " + texto)
                + (faltando.isEmpty() ? "" : " (sem o parecer de " + String.join(", ", faltando) + ")");
        ProspeccaoEntity atualizado = prospeccaoService.buscar(id);
        prospeccaoService.registrarEvento(atualizado, TipoEventoProspeccao.COMITE_RESULTADO, null, null, null, historico, autor);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.ComiteDecidiu(atualizado, resultado, membros, autor, texto));
        return movido;
    }

    // ---------------------------------------------------------------- contrato

    @Transactional
    public ProspeccaoEntity salvarContrato(UUID id, String nome, String telefone, String email, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        autorizacao.exigirEscrita(card, autor);
        if (card.getEstagio().terminal()) {
            throw new TransicaoInvalidaException("Card encerrado: reabra antes de editar o contrato.");
        }
        card.setContratoContatoNome(limpar(nome, 200));
        card.setContratoContatoTelefone(limpar(telefone, 40));
        String emailLimpo = limpar(email, 200);
        if (emailLimpo != null && !emailLimpo.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("E-mail do contato inválido.");
        }
        card.setContratoContatoEmail(emailLimpo);
        card.setUpdatedAt(LocalDateTime.now());
        ProspeccaoEntity salvo = prospeccaoRepository.save(card);
        prospeccaoService.registrarEvento(salvo, TipoEventoProspeccao.CONTRATO, null, null, null,
                "Contato do contrato: " + (salvo.getContratoContatoNome() == null ? "—" : salvo.getContratoContatoNome()), autor);
        return salvo;
    }

    /** Fecha o card em HABILITADO. Exige o contato de quem assina, como no Portal Comercial. */
    @Transactional
    public ProspeccaoEntity concluir(UUID id, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        if (card.getEstagio() != EstagioProspeccao.CONTRATO) {
            throw new TransicaoInvalidaException("Só se conclui um card em contrato.");
        }
        autorizacao.exigirDona(card, autor);
        if (card.getContratoContatoNome() == null || card.getContratoContatoTelefone() == null
                || card.getContratoContatoEmail() == null) {
            throw new TransicaoInvalidaException("Preencha nome, telefone e e-mail de quem assina o contrato antes de concluir.");
        }
        ProspeccaoEntity movido = prospeccaoService.mover(id, EstagioProspeccao.HABILITADO, null, null, autor);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.Concluido(movido, autor));
        return movido;
    }

    // ------------------------------------------------------------------ leitura

    @Transactional(readOnly = true)
    public List<ProspeccaoParecerEntity> pareceres(UUID id) {
        return parecerRepository.findByProspeccaoIdOrderByRodadaDescCriadoEmAsc(id);
    }

    /** Pareceres da rodada vigente por card, para o quadro mostrar "2/3". */
    @Transactional(readOnly = true)
    public Map<UUID, List<ProspeccaoParecerEntity>> rodadaVigente(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        return parecerRepository.rodadaVigente(cardIds).stream()
                .collect(Collectors.groupingBy(ProspeccaoParecerEntity::getProspeccaoId));
    }

    @Transactional(readOnly = true)
    public List<UUID> cardsAguardandoParecerDe(UserEntity usuario) {
        return usuario == null || usuario.getId() == null ? List.of() : parecerRepository.cardsAguardandoParecerDe(usuario.getId());
    }

    private List<ProspeccaoParecerEntity> rodada(ProspeccaoEntity card) {
        return parecerRepository.findByProspeccaoIdAndRodadaOrderByCriadoEmAsc(card.getId(), card.getRodadaComite());
    }

    static List<String> aguardando(List<ProspeccaoParecerEntity> rodada) {
        return rodada.stream().filter(parecer -> !parecer.registrado()).map(ProspeccaoParecerEntity::getUsuarioNome).toList();
    }

    static String rotulo(PosicaoParecer posicao) {
        return switch (posicao) {
            case FAVORAVEL -> "Favorável";
            case COM_RESSALVAS -> "Com ressalvas";
            case DESFAVORAVEL -> "Desfavorável";
        };
    }

    private static String limpar(String texto, int limite) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        if (limpo.length() > limite) {
            throw new IllegalArgumentException("Texto passa de " + limite + " caracteres.");
        }
        return limpo;
    }
}
