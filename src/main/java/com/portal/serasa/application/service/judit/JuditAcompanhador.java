package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.integration.judit.JuditClient;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Acompanha os pedidos assíncronos da Judit — detalhe de processo e On Demand — até terminarem, grava
 * a resposta e avisa quem pediu pelo sino.
 *
 * <p>A Judit também avisaria por webhook, mas o servidor de produção fica na rede interna — a Judit não
 * alcança. Então um job lê do banco os pedidos PENDENTE e pergunta a situação, com intervalo que cresce
 * com a idade do pedido (perguntar não cobra). Como lê do banco, reiniciar o servidor não perde pedido:
 * o job retoma na volta.</p>
 *
 * <p>Passado o limite de cada serviço, o job para de perguntar e o pedido fica PENDENTE — pode só estar
 * demorando. O "verificar agora" da tela pergunta mais uma vez. Os dois não correm juntos no mesmo
 * pedido, e quem conclui é quem notifica.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JuditAcompanhador {

    private static final String ATOR = "Judit";

    /** Idade do pedido a partir da qual o intervalo entre perguntas cresce. */
    static final Duration PRIMEIRA_FAIXA = Duration.ofMinutes(2);
    static final Duration SEGUNDA_FAIXA = Duration.ofMinutes(10);
    static final Duration INTERVALO_SEGUNDA_FAIXA = Duration.ofSeconds(30);
    static final Duration INTERVALO_DEPOIS = Duration.ofMinutes(2);

    private final JuditClient client;
    private final JuditService juditService;
    private final JuditProperties properties;
    private final NotificacaoService notificacaoService;

    private final Set<UUID> emAndamento = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedDelayString = "${judit.acompanhamento-intervalo-segundos:5}", timeUnit = TimeUnit.SECONDS)
    public void acompanharPendentes() {
        LocalDateTime agora = LocalDateTime.now();
        int maiorLimite = Math.max(properties.acompanhamentoLimiteMinutos(), properties.acompanhamentoOnDemandLimiteMinutos());
        List<JuditService.Pendente> pendentes;
        try {
            pendentes = juditService.pendentesDesde(agora.minusMinutes(maiorLimite));
        } catch (RuntimeException e) {
            log.warn("Judit: falha ao ler os pedidos pendentes ({})", e.getMessage());
            return;
        }
        pendentes.stream().filter(p -> eHora(p, agora)).forEach(this::verificar);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoPedirVerificacao(JuditService.VerificacaoPedida pedido) {
        verificar(pedido.pendente());
    }

    /** Dentro do limite do serviço e passado o intervalo da faixa de idade desde a última pergunta. */
    boolean eHora(JuditService.Pendente p, LocalDateTime agora) {
        Duration idade = Duration.between(p.criadoEm(), agora);
        if (idade.compareTo(Duration.ofMinutes(limiteMinutos(p.servico()))) > 0) {
            return false;
        }
        if (p.verificadoEm() == null) {
            return true;
        }
        Duration intervalo = idade.compareTo(PRIMEIRA_FAIXA) < 0
                ? Duration.ofSeconds(properties.acompanhamentoIntervaloSegundos())
                : idade.compareTo(SEGUNDA_FAIXA) < 0 ? INTERVALO_SEGUNDA_FAIXA : INTERVALO_DEPOIS;
        return !p.verificadoEm().plus(intervalo).isAfter(agora);
    }

    private int limiteMinutos(ServicoJudit servico) {
        return servico == ServicoJudit.ON_DEMAND
                ? properties.acompanhamentoOnDemandLimiteMinutos()
                : properties.acompanhamentoLimiteMinutos();
    }

    /** Pergunta uma vez. Concluído, busca e grava; erro na Judit, marca falha; senão, espera a próxima. */
    void verificar(JuditService.Pendente p) {
        if (!emAndamento.add(p.consultaId())) {
            return;
        }
        try {
            juditService.marcarVerificado(p.consultaId(), LocalDateTime.now());
            String situacao = situacao(p);
            if ("completed".equals(situacao)) {
                concluir(p);
            } else if (falhou(situacao)) {
                falhar(p, "A Judit encerrou o pedido com a situação \"" + situacao + "\".");
            }
        } catch (RuntimeException e) {
            // Um pedido com problema não pode parar o job dos outros.
            log.warn("Judit {} {}: falha ao acompanhar ({})", p.servico(), p.consultaId(), e.getMessage());
        } finally {
            emAndamento.remove(p.consultaId());
        }
    }

    /** Falha de rede ao perguntar não encerra: tenta de novo na próxima volta. */
    private String situacao(JuditService.Pendente p) {
        try {
            return client.situacaoDoPedido(p.requestId()).toLowerCase(Locale.ROOT);
        } catch (ExternalApiException e) {
            log.warn("Judit {} {}: falha ao perguntar a situação ({})", p.servico(), p.consultaId(), e.getMessage());
            return "";
        }
    }

    private static boolean falhou(String situacao) {
        return situacao.contains("error") || situacao.contains("fail") || situacao.contains("cancel");
    }

    private void concluir(JuditService.Pendente p) {
        JsonNode respostas;
        try {
            respostas = client.respostasDoPedido(p.requestId());
        } catch (ExternalApiException e) {
            falhar(p, "A Judit terminou, mas a resposta não pôde ser lida: " + e.getMessage());
            return;
        }
        if (p.servico() == ServicoJudit.ON_DEMAND) {
            concluirOnDemand(p, respostas);
        } else {
            concluirDetalhe(p, respostas);
        }
    }

    private void concluirDetalhe(JuditService.Pendente p, JsonNode respostas) {
        if (!juditService.concluirDetalhe(p.consultaId(), respostas)) {
            return;
        }
        JuditLeitura.Detalhe detalhe = JuditLeitura.detalhe(respostas);
        String resumo;
        if (detalhe.processo() == null) {
            resumo = "A Judit não encontrou o processo no tribunal.";
        } else {
            int andamentos = detalhe.processo().movimentacoes().size();
            resumo = (detalhe.resumoIa() != null ? "Resumo de IA e " : "") + andamentos
                    + (andamentos == 1 ? " movimentação pronta." : " movimentações prontas.");
        }
        notificar(p, TipoNotificacao.JUDIT_PROCESSO_PRONTO, "Processo detalhado pela Judit", p.cnj() + quem(p) + " — " + resumo);
        log.info("Judit DETALHE {} concluído", p.cnj());
    }

    private void concluirOnDemand(JuditService.Pendente p, JsonNode respostas) {
        JuditService.OnDemandConcluido concluido = juditService.concluirOnDemand(p.consultaId(), respostas);
        if (concluido == null) {
            return;
        }
        String como = p.polo() == PoloJudit.ATIVO ? " como autora" : " como ré";
        String resumo = concluido.processos() == 0
                ? "Nenhum processo" + como + "."
                : concluido.processos() + (concluido.processos() == 1 ? " processo" : " processos") + como
                + " · custo " + reais(concluido.custo()) + ".";
        String nome = p.nomeEmpresa() == null || p.nomeEmpresa().isBlank() ? "Empresa " + p.documento() : p.nomeEmpresa();
        notificar(p, TipoNotificacao.JUDIT_CONSULTA_PRONTA, "Processos da Judit prontos", nome + " — " + resumo);
        log.info("Judit ON_DEMAND {} de {} concluído: {} registros, custo {}", p.polo(), p.documento(),
                concluido.registros(), concluido.custo());
    }

    private void falhar(JuditService.Pendente p, String erro) {
        if (!juditService.falhar(p.consultaId(), erro)) {
            return;
        }
        if (p.servico() == ServicoJudit.ON_DEMAND) {
            String nome = p.nomeEmpresa() == null || p.nomeEmpresa().isBlank() ? "Empresa " + p.documento() : p.nomeEmpresa();
            notificar(p, TipoNotificacao.JUDIT_CONSULTA_FALHOU, "A Judit não conseguiu consultar os processos", nome + " — " + erro);
        } else {
            notificar(p, TipoNotificacao.JUDIT_PROCESSO_FALHOU, "A Judit não conseguiu detalhar o processo",
                    p.cnj() + quem(p) + " — " + erro);
        }
        log.warn("Judit {} {} falhou: {}", p.servico(), p.consultaId(), erro);
    }

    /**
     * Ator nulo: o serviço de notificação nunca avisa quem fez a ação, e aqui quem pediu é
     * justamente quem precisa saber. O nome que aparece é "Judit".
     */
    private void notificar(JuditService.Pendente p, TipoNotificacao tipo, String titulo, String texto) {
        String link = "/clients/" + p.documento() + "/processos"
                + (p.cnj() != null ? "?processo=" + codificar(p.cnj()) : "")
                + (p.nomeEmpresa() == null || p.nomeEmpresa().isBlank() ? ""
                : (p.cnj() != null ? "&" : "?") + "nome=" + codificar(p.nomeEmpresa()));
        try {
            var entregas = notificacaoService.notificar(
                    List.of(new NotificacaoService.Nova(p.usuarioId(), tipo, titulo, texto, link)), null, ATOR);
            notificacaoService.entregar(entregas);
        } catch (RuntimeException e) {
            // A resposta já está gravada; sem o aviso, a pessoa ainda encontra na tela.
            log.warn("Judit {} {}: falha ao notificar ({})", p.servico(), p.consultaId(), e.getMessage());
        }
    }

    private static String quem(JuditService.Pendente p) {
        return p.nomeEmpresa() == null || p.nomeEmpresa().isBlank() ? "" : " · " + p.nomeEmpresa();
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }

    private static String reais(BigDecimal valor) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor).replace(' ', ' ');
    }
}
