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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Acompanha o pedido até a Judit terminar, grava e avisa quem pediu — uma vez só. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JuditAcompanhadorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CNJ = "1009479-30.2024.8.26.0011";

    @Mock private JuditClient client;
    @Mock private JuditService juditService;
    @Mock private NotificacaoService notificacaoService;

    private JuditAcompanhador acompanhador;
    private JuditService.DetalhePedido pedido;

    @BeforeEach
    void setUp() {
        acompanhador = acompanhadorCom(1);
        pedido = new JuditService.DetalhePedido(UUID.randomUUID(), "req-9", UUID.randomUUID(), CNJ,
                "12021039000174", "I G LABEGALINI LTDA EPP");
        when(notificacaoService.notificar(any(), any(), anyString())).thenReturn(List.of());
    }

    private JuditAcompanhador acompanhadorCom(int limiteMinutos) {
        JuditProperties properties = new JuditProperties("chave", "l", "r", BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("0.35"), BigDecimal.TEN, BigDecimal.TEN, 30, 0, limiteMinutos);
        return new JuditAcompanhador(client, juditService, properties, notificacaoService);
    }

    @SuppressWarnings("unchecked")
    private NotificacaoService.Nova notificacaoEnviada() {
        ArgumentCaptor<Collection<NotificacaoService.Nova>> novas = ArgumentCaptor.forClass(Collection.class);
        verify(notificacaoService).notificar(novas.capture(), isNull(), eq("Judit"));
        assertThat(novas.getValue()).hasSize(1);
        return novas.getValue().iterator().next();
    }

    @Test
    @DisplayName("pendente → completed: grava a resposta e notifica quem pediu, com link para o processo")
    void conclui() throws Exception {
        JsonNode respostas = JSON.readTree("""
                {"page_data": [{"response_type": "summary", "response_data": {"data": ["## Resumo"]}},
                               {"response_type": "lawsuit", "response_data": {"code": "x", "steps": [{"step_id": "1"}, {"step_id": "2"}]}}]}""");
        when(client.situacaoDoPedido("req-9")).thenReturn("pending", "pending", "completed");
        when(client.respostasDoPedido("req-9")).thenReturn(respostas);
        when(juditService.concluirDetalhe(pedido.consultaId(), respostas)).thenReturn(true);

        acompanhador.acompanhar(pedido);

        verify(client, times(3)).situacaoDoPedido("req-9");
        NotificacaoService.Nova nova = notificacaoEnviada();
        assertThat(nova.destinatarioId()).isEqualTo(pedido.usuarioId());
        assertThat(nova.tipo()).isEqualTo(TipoNotificacao.JUDIT_PROCESSO_PRONTO);
        assertThat(nova.resumo()).contains(CNJ, "I G LABEGALINI LTDA EPP", "Resumo de IA e 2 movimentações prontas");
        assertThat(nova.link()).isEqualTo(
                "/clients/12021039000174/processos?processo=1009479-30.2024.8.26.0011&nome=I+G+LABEGALINI+LTDA+EPP");
        verify(notificacaoService).entregar(any());
    }

    @Test
    @DisplayName("falha de rede ao perguntar não encerra: tenta de novo")
    void redeOscila() throws Exception {
        when(client.situacaoDoPedido("req-9"))
                .thenThrow(new ExternalApiException(JuditClient.PROVIDER, 0, "tempo", null))
                .thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenReturn(JSON.readTree("{\"page_data\": []}"));
        when(juditService.concluirDetalhe(any(), any())).thenReturn(true);

        acompanhador.acompanhar(pedido);

        assertThat(notificacaoEnviada().resumo()).contains("não encontrou o processo");
    }

    @Test
    @DisplayName("Judit devolve erro: marca falha e avisa")
    void falha() {
        when(client.situacaoDoPedido("req-9")).thenReturn("error");
        when(juditService.falharDetalhe(any(), anyString())).thenReturn(true);

        acompanhador.acompanhar(pedido);

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

        acompanhador.acompanhar(pedido);

        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("passou do tempo: deixa pendente, sem falha nem aviso (o 'verificar agora' retoma)")
    void tempoEsgotado() {
        acompanhador = acompanhadorCom(0);
        when(client.situacaoDoPedido("req-9")).thenReturn("pending");

        acompanhador.acompanhar(pedido);

        verify(juditService, never()).falharDetalhe(any(), any());
        verify(juditService, never()).concluirDetalhe(any(), any());
        verify(notificacaoService, never()).notificar(any(), any(), any());
    }
}
