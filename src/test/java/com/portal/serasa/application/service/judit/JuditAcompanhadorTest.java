package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.domain.model.ServicoJudit;
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

/** Pergunta à Judit pelos pedidos pendentes lidos do banco, grava e avisa quem pediu — uma vez só. */
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
    private JuditService.Pendente detalhe;
    private JuditService.Pendente onDemand;

    @BeforeEach
    void setUp() {
        // Intervalo de 5 s, detalhe até 10 min, On Demand até 360 min.
        JuditProperties properties = new JuditProperties("chave", "l", "r", BigDecimal.ONE,
                new BigDecimal("6.00"), new BigDecimal("0.35"), BigDecimal.TEN, BigDecimal.TEN, 30, 5, 10, 360);
        acompanhador = new JuditAcompanhador(client, juditService, properties, notificacaoService);
        detalhe = pendente(ServicoJudit.DETALHE, CNJ, null, LocalDateTime.now(), null);
        onDemand = pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO, LocalDateTime.now(), null);
        when(notificacaoService.notificar(any(), any(), anyString())).thenReturn(List.of());
    }

    private static JuditService.Pendente pendente(ServicoJudit servico, String cnj, PoloJudit polo,
                                                  LocalDateTime criadoEm, LocalDateTime verificadoEm) {
        return new JuditService.Pendente(UUID.randomUUID(), servico, "req-9", UUID.randomUUID(), CNPJ, cnj, polo,
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
    @DisplayName("detalhe completed: grava a resposta e notifica quem pediu, com link para o processo")
    void concluiDetalhe() throws Exception {
        JsonNode respostas = JSON.readTree("""
                {"page_data": [{"response_type": "summary", "response_data": {"data": ["## Resumo"]}},
                               {"response_type": "lawsuit", "response_data": {"code": "x", "steps": [{"step_id": "1"}, {"step_id": "2"}]}}]}""");
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenReturn(respostas);
        when(juditService.concluirDetalhe(detalhe.consultaId(), respostas)).thenReturn(true);

        acompanhador.verificar(detalhe);

        verify(juditService).marcarVerificado(eq(detalhe.consultaId()), any());
        NotificacaoService.Nova nova = notificacaoEnviada();
        assertThat(nova.destinatarioId()).isEqualTo(detalhe.usuarioId());
        assertThat(nova.tipo()).isEqualTo(TipoNotificacao.JUDIT_PROCESSO_PRONTO);
        assertThat(nova.resumo()).contains(CNJ, "I G LABEGALINI LTDA EPP", "Resumo de IA e 2 movimentações prontas");
        assertThat(nova.link()).isEqualTo(
                "/clients/12021039000174/processos?processo=1009479-30.2024.8.26.0011&nome=I+G+LABEGALINI+LTDA+EPP");
        verify(notificacaoService).entregar(any());
    }

    @Test
    @DisplayName("On Demand completed: grava e avisa quantos processos como ré e o custo real")
    void concluiOnDemand() throws Exception {
        JsonNode respostas = JSON.readTree("{\"page_data\": []}");
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenReturn(respostas);
        when(juditService.concluirOnDemand(onDemand.consultaId(), respostas))
                .thenReturn(new JuditService.OnDemandConcluido(5, 5, new BigDecimal("6.00")));

        acompanhador.verificar(onDemand);

        NotificacaoService.Nova nova = notificacaoEnviada();
        assertThat(nova.tipo()).isEqualTo(TipoNotificacao.JUDIT_CONSULTA_PRONTA);
        assertThat(nova.resumo()).contains("I G LABEGALINI LTDA EPP", "5 processos como ré", "R$ 6,00");
        assertThat(nova.link()).isEqualTo("/clients/12021039000174/processos?nome=I+G+LABEGALINI+LTDA+EPP");
        verify(juditService, never()).concluirDetalhe(any(), any());
    }

    @Test
    @DisplayName("ainda pendente: só marca que perguntou, sem gravar nem avisar")
    void aindaPendente() {
        when(client.situacaoDoPedido("req-9")).thenReturn("pending");

        acompanhador.verificar(onDemand);

        verify(juditService).marcarVerificado(eq(onDemand.consultaId()), any());
        verify(juditService, never()).concluirOnDemand(any(), any());
        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("falha de rede ao perguntar não encerra: a próxima volta pergunta de novo")
    void redeOscila() {
        when(client.situacaoDoPedido("req-9")).thenThrow(new ExternalApiException(JuditClient.PROVIDER, 0, "tempo", null));

        acompanhador.verificar(detalhe);

        verify(juditService, never()).falhar(any(), any());
        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("Judit devolve erro: marca falha e avisa, com o tipo de cada serviço")
    void falha() {
        when(client.situacaoDoPedido("req-9")).thenReturn("error");
        when(juditService.falhar(any(), anyString())).thenReturn(true);

        acompanhador.verificar(onDemand);

        verify(juditService).falhar(eq(onDemand.consultaId()), anyString());
        assertThat(notificacaoEnviada().tipo()).isEqualTo(TipoNotificacao.JUDIT_CONSULTA_FALHOU);
        verify(client, never()).respostasDoPedido(any());
    }

    @Test
    @DisplayName("outro acompanhamento já concluiu: não notifica de novo")
    void jaConcluido() throws Exception {
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenReturn(JSON.readTree("{\"page_data\": []}"));
        when(juditService.concluirDetalhe(any(), any())).thenReturn(false);

        acompanhador.verificar(detalhe);

        verify(notificacaoService, never()).notificar(any(), any(), any());
    }

    @Test
    @DisplayName("hora de perguntar: o intervalo cresce com a idade, e cada serviço tem seu limite")
    void eHora() {
        LocalDateTime agora = LocalDateTime.now();

        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO, agora, null), agora)).isTrue();
        // Até 2 min: a cada 5 s.
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(1), agora.minusSeconds(3)), agora)).isFalse();
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(1), agora.minusSeconds(5)), agora)).isTrue();
        // De 2 a 10 min: a cada 30 s.
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(5), agora.minusSeconds(20)), agora)).isFalse();
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(5), agora.minusSeconds(31)), agora)).isTrue();
        // Depois: a cada 2 min.
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(30), agora.minusMinutes(1)), agora)).isFalse();
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(30), agora.minusMinutes(2)), agora)).isTrue();
        // Limite: detalhe para em 10 min; On Demand segue até 360.
        assertThat(acompanhador.eHora(pendente(ServicoJudit.DETALHE, CNJ, null, agora.minusMinutes(11), null), agora)).isFalse();
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(11), null), agora)).isTrue();
        assertThat(acompanhador.eHora(pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO,
                agora.minusMinutes(361), null), agora)).isFalse();
    }

    @Test
    @DisplayName("job: lê os pendentes do banco (sobrevive a reinício) e pergunta só os que estão na hora")
    void jobLeDoBanco() {
        JuditService.Pendente naHora = pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO, LocalDateTime.now(), null);
        JuditService.Pendente cedo = new JuditService.Pendente(UUID.randomUUID(), ServicoJudit.ON_DEMAND, "req-cedo",
                UUID.randomUUID(), CNPJ, null, PoloJudit.ATIVO, null, LocalDateTime.now(), LocalDateTime.now());
        when(juditService.pendentesDesde(any())).thenReturn(List.of(naHora, cedo));
        when(client.situacaoDoPedido(anyString())).thenReturn("pending");

        acompanhador.acompanharPendentes();

        verify(client).situacaoDoPedido("req-9");
        verify(client, never()).situacaoDoPedido("req-cedo");
    }

    @Test
    @DisplayName("job: erro inesperado num pedido não impede os outros")
    void jobSegueDepoisDeErro() {
        JuditService.Pendente quebra = pendente(ServicoJudit.ON_DEMAND, null, PoloJudit.PASSIVO, LocalDateTime.now(), null);
        JuditService.Pendente bom = new JuditService.Pendente(UUID.randomUUID(), ServicoJudit.DETALHE, "req-bom",
                UUID.randomUUID(), CNPJ, CNJ, null, null, LocalDateTime.now(), null);
        when(juditService.pendentesDesde(any())).thenReturn(List.of(quebra, bom));
        when(client.situacaoDoPedido("req-9")).thenReturn("completed");
        when(client.respostasDoPedido("req-9")).thenThrow(new IllegalStateException("payload estranho"));
        when(client.situacaoDoPedido("req-bom")).thenReturn("pending");

        acompanhador.acompanharPendentes();

        verify(client).situacaoDoPedido("req-bom");
    }
}
