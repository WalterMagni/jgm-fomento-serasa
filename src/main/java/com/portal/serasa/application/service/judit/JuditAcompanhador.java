package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.integration.judit.JuditClient;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Acompanha o detalhe de processo até a Judit terminar e avisa quem pediu pelo sino.
 *
 * <p>O detalhe é assíncrono do lado da Judit (~1 minuto no teste). Ela também avisaria por webhook,
 * mas o servidor de produção fica na rede interna — a Judit não alcança. Então o backend pergunta a
 * situação a cada {@code judit.acompanhamento-intervalo-segundos}, por até
 * {@code judit.acompanhamento-limite-minutos}. Perguntar não cobra.</p>
 *
 * <p>Se o servidor reiniciar no meio, a consulta fica PENDENTE e o "verificar agora" da tela
 * retoma. Duas retomadas da mesma consulta não correm juntas.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JuditAcompanhador {

    private static final String ATOR = "Judit";

    private final JuditClient client;
    private final JuditService juditService;
    private final JuditProperties properties;
    private final NotificacaoService notificacaoService;

    private final Set<UUID> emAndamento = ConcurrentHashMap.newKeySet();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void aoPedir(JuditService.DetalhePedido pedido) {
        acompanhar(pedido);
    }

    void acompanhar(JuditService.DetalhePedido pedido) {
        if (!emAndamento.add(pedido.consultaId())) {
            return;
        }
        try {
            Instant fim = Instant.now().plus(Duration.ofMinutes(properties.acompanhamentoLimiteMinutos()));
            while (true) {
                String situacao = situacao(pedido);
                if ("completed".equals(situacao)) {
                    concluir(pedido);
                    return;
                }
                if (falhou(situacao)) {
                    falhar(pedido, "A Judit encerrou o pedido com a situação \"" + situacao + "\".");
                    return;
                }
                if (Instant.now().isAfter(fim)) {
                    // Fica PENDENTE: pode só estar demorando. A tela oferece "verificar agora".
                    log.warn("Judit DETALHE {} ainda '{}' depois de {} min", pedido.cnj(), situacao,
                            properties.acompanhamentoLimiteMinutos());
                    return;
                }
                if (!esperar()) {
                    return;
                }
            }
        } finally {
            emAndamento.remove(pedido.consultaId());
        }
    }

    /** Falha de rede ao perguntar não encerra: tenta de novo na próxima volta. */
    private String situacao(JuditService.DetalhePedido pedido) {
        try {
            return client.situacaoDoPedido(pedido.requestId()).toLowerCase(Locale.ROOT);
        } catch (ExternalApiException e) {
            log.warn("Judit DETALHE {}: falha ao perguntar a situação ({})", pedido.cnj(), e.getMessage());
            return "";
        }
    }

    private static boolean falhou(String situacao) {
        return situacao.contains("error") || situacao.contains("fail") || situacao.contains("cancel");
    }

    private void concluir(JuditService.DetalhePedido pedido) {
        JsonNode respostas;
        try {
            respostas = client.respostasDoPedido(pedido.requestId());
        } catch (ExternalApiException e) {
            falhar(pedido, "A Judit terminou, mas a resposta não pôde ser lida: " + e.getMessage());
            return;
        }
        if (!juditService.concluirDetalhe(pedido.consultaId(), respostas)) {
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
        notificar(pedido, TipoNotificacao.JUDIT_PROCESSO_PRONTO, "Processo detalhado pela Judit", resumo);
        log.info("Judit DETALHE {} concluído", pedido.cnj());
    }

    private void falhar(JuditService.DetalhePedido pedido, String erro) {
        if (juditService.falharDetalhe(pedido.consultaId(), erro)) {
            notificar(pedido, TipoNotificacao.JUDIT_PROCESSO_FALHOU, "A Judit não conseguiu detalhar o processo", erro);
            log.warn("Judit DETALHE {} falhou: {}", pedido.cnj(), erro);
        }
    }

    /**
     * Ator nulo: o serviço de notificação nunca avisa quem fez a ação, e aqui quem pediu é
     * justamente quem precisa saber. O nome que aparece é "Judit".
     */
    private void notificar(JuditService.DetalhePedido pedido, TipoNotificacao tipo, String titulo, String texto) {
        String quem = pedido.nomeEmpresa() == null || pedido.nomeEmpresa().isBlank() ? "" : " · " + pedido.nomeEmpresa();
        String link = "/clients/" + pedido.cnpj() + "/processos?processo=" + codificar(pedido.cnj())
                + (quem.isEmpty() ? "" : "&nome=" + codificar(pedido.nomeEmpresa()));
        try {
            var entregas = notificacaoService.notificar(
                    List.of(new NotificacaoService.Nova(pedido.usuarioId(), tipo, titulo, pedido.cnj() + quem + " — " + texto, link)),
                    null, ATOR);
            notificacaoService.entregar(entregas);
        } catch (RuntimeException e) {
            // O detalhe já está gravado; sem o aviso, a pessoa ainda encontra na tela.
            log.warn("Judit DETALHE {}: falha ao notificar ({})", pedido.cnj(), e.getMessage());
        }
    }

    private boolean esperar() {
        try {
            Thread.sleep(properties.acompanhamentoIntervaloSegundos() * 1000L);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String codificar(String valor) {
        return URLEncoder.encode(valor, StandardCharsets.UTF_8);
    }
}
