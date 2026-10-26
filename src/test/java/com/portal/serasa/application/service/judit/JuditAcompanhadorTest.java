package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.integration.judit.JuditClient;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pergunta à Judit pelos detalhes pendentes lidos do banco, grava e avisa quem pediu — uma vez só. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JuditAcompanhadorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CNJ = "1009479-30.2024.8.26.0011";
    private static final String CNPJ = "12021039000174";

    @Mock private JuditClient client;
    @Mock private JuditService juditService;
    @Mock private NotificacaoService notificacaoService;

    private JuditAcompanhador acompanhador;
    private JuditService.Pendente pedido;

    @BeforeEach
    void setUp() {
        // Intervalo de 5 s, limite de 10 min.
        JuditProperties properties = new JuditProperties("chave", "l", "r", BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("0.35"), BigDecimal.TEN, BigDecimal.TEN, 30, 5, 10);
        acompanhador = new JuditAcompanhador(client, juditService, properties, notificacaoService);
        pedido = pendente("req-9", LocalDateTime.now(), null);
        when(notificacaoService.notificar(any(), any(), anyString())).thenReturn(List.of());
    }

    private static JuditService.Pendente pendente(String requestId, LocalDateTime criadoEm, LocalDateTime verificadoEm) {
        return new JuditService.Pendente(UUID.randomUUID(), requestId, UUID.randomUUID(), CNPJ, CNJ,
                "I G LABEGALINI LTDA EPP", criadoEm, verificadoEm);
    }

    @SuppressWarnings("unchecked")
    private NotificacaoService.Nova notificacaoEnviada() {
        ArgumentCaptor<Collection<NotificacaoService.Nova>> novas = ArgumentCaptor.forClass(Collection.class);
        verify(notificacaoService).notificar(novas.capture(), isNull(), eq("Judit"));
        assertThat(novas.getValue()).hasSize(1);
        return novas.getValue().iterator().next();
    }

    @Test
    @DisplayName("completed: grava a resposta e notifica quem pediu, com link para o processo")
    void conclui() throws Exception {
        JsonNode respostas = JSON.readTree("""
                {"page_data": [{"response_type": "summary", "response_data": {"data": ["## Resumo"]}},
                               {"response_type": "lawsuit", "response_data": {"code": "x", "steps": [{"step_id": "1"}, {"step_id": "2"}]}}]}""");
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenReturn(respostas);
        when(juditService.concluirDetalhe(pedido.consultaId(), respostas)).thenReturn(true);

        acompanhador.verificar(pedido);

        verify(juditService).marcarVerificado(eq(pedido.consultaId()), any());
        NotificacaoService.Nova nova = notificacaoEnviada();
        assertThat(nova.destinatarioId()).isEqualTo(pedido.usuarioId());
        assertThat(nova.tipo()).isEqualTo(TipoNotificacao.JUDIT_PROCESSO_PRONTO);
        assertThat(nova.resumo()).contains(CNJ, "I G LABEGALINI LTDA EPP", "Resumo de IA e 2 movimentações prontas");
        assertThat(nova.link()).isEqualTo(
                "/clients/12021039000174/processos?processo=1009479-30.2024.8.26.0011&nome=I+G+LABEGALINI+LTDA+EPP");
        verify(notificacaoService).entregar(any());
    }

    @Test
    @DisplayName("ainda pendente: só marca que perguntou, sem gravar nem avisar")
    void aindaPendente() {
        when(client.situacaoDoPedido("req-9")).thenReturn("pending");

        acompanhador.verificar(pedido);

        verify(juditService).marcarVerificado(eq(pedido.consultaId()), any());
        verify(juditService, never()).concluirDetalhe(any(), any());
        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("falha de rede ao perguntar não encerra: a próxima volta pergunta de novo")
    void redeOscila() {
        when(client.situacaoDoPedido("req-9")).thenThrow(new ExternalApiException(JuditClient.PROVIDER, 0, "tempo", null));

        acompanhador.verificar(pedido);

        verify(juditService, never()).falharDetalhe(any(), any());
        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("Judit devolve erro: marca falha e avisa")
    void falha() {
        when(client.situacaoDoPedido("req-9")).thenReturn("error");
        when(juditService.falharDetalhe(any(), anyString())).thenReturn(true);

        acompanhador.verificar(pedido);

        verify(juditService).falharDetalhe(eq(pedido.consultaId()), anyString());
        assertThat(notificacaoEnviada().tipo()).isEqualTo(TipoNotificacao.JUDIT_PROCESSO_FALHOU);
        verify(client, never()).respostasDoPedido(any());
    }

    @Test
    @DisplayName("outro acompanhamento já concluiu: não notifica de novo")
    void jaConcluido() throws Exception {
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenReturn(JSON.readTree("{\"page_data\": []}"));
        when(juditService.concluirDetalhe(any(), any())).thenReturn(false);

        acompanhador.verificar(pedido);

        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("hora de perguntar: a cada 5 s nos 2 primeiros minutos, depois a cada 30 s, até 10 min")
    void eHora() {
        LocalDateTime agora = LocalDateTime.now();

        assertThat(acompanhador.eHora(pendente("r", agora, null), agora)).isTrue();
        assertThat(acompanhador.eHora(pendente("r", agora.minusMinutes(1), agora.minusSeconds(3)), agora)).isFalse();
        assertThat(acompanhador.eHora(pendente("r", agora.minusMinutes(1), agora.minusSeconds(5)), agora)).isTrue();
        assertThat(acompanhador.eHora(pendente("r", agora.minusMinutes(5), agora.minusSeconds(20)), agora)).isFalse();
        assertThat(acompanhador.eHora(pendente("r", agora.minusMinutes(5), agora.minusSeconds(31)), agora)).isTrue();
        assertThat(acompanhador.eHora(pendente("r", agora.minusMinutes(11), null), agora)).isFalse();
    }

    @Test
    @DisplayName("job: lê os pendentes do banco (sobrevive a reinício) e pergunta só os que estão na hora")
    void jobLeDoBanco() {
        JuditService.Pendente cedo = pendente("req-cedo", LocalDateTime.now(), LocalDateTime.now());
        when(juditService.pendentesDesde(any())).thenReturn(List.of(pedido, cedo));
        when(client.situacaoDoPedido(anyString())).thenReturn("pending");

        acompanhador.acompanharPendentes();

        verify(client).situacaoDoPedido("req-9");
        verify(client, never()).situacaoDoPedido("req-cedo");
    }

    @Test
    @DisplayName("job: erro inesperado num pedido não impede os outros")
    void jobSegueDepoisDeErro() {
        JuditService.Pendente bom = pendente("req-bom", LocalDateTime.now(), null);
        when(juditService.pendentesDesde(any())).thenReturn(List.of(pedido, bom));
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenThrow(new IllegalStateException("payload estranho"));
        when(client.situacaoDoPedido("req-bom")).thenReturn("pending");

        acompanhador.acompanharPendentes();

        verify(client).situacaoDoPedido("req-bom");
    }
}
