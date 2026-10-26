package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.exception.LimiteJuditException;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import com.portal.serasa.infrastructure.integration.judit.JuditClient;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import com.portal.serasa.infrastructure.persistence.entity.JuditConsultaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.JuditConsultaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.JuditLiberacaoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Travas antes de cada chamada cobrada (marca Judit, limite do mês), o custo registrado e o On Demand. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JuditServiceTest {

    private static final String CNPJ = "12021039000174";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private JuditClient client;
    @Mock private JuditConsultaJpaRepository consultas;
    @Mock private JuditLiberacaoJpaRepository liberacoes;
    @Mock private ApplicationEventPublisher eventos;

    private JuditService service;
    private UserEntity analistaJudit;

    @BeforeEach
    void setUp() {
        // Limite de R$ 1.000 aqui para os testes de trava lerem redondo; em produção é R$ 1.500.
        JuditProperties properties = new JuditProperties("chave", "https://lawsuits.example", "https://requests.example",
                new BigDecimal("0.75"), new BigDecimal("6.00"), new BigDecimal("0.35"),
                new BigDecimal("1000"), new BigDecimal("1000"), 30, 0, 1, 360);
        service = new JuditService(client, properties, consultas, new JuditFranquia(consultas, liberacoes, properties),
                JSON, eventos);
        analistaJudit = UserEntity.builder().id(UUID.randomUUID()).name("Ana").email("ana@jgm.com.br")
                .analista(true).judit(true).build();
        when(consultas.somarCustoDesde(any())).thenReturn(BigDecimal.ZERO);
        when(liberacoes.somarDoMes(any())).thenReturn(BigDecimal.ZERO);
        when(consultas.findFirstByDocumentoAndServicoOrderByCriadoEmDesc(anyString(), any())).thenReturn(Optional.empty());
    }

    private ObjectNode resumoCom(int total) {
        ObjectNode resumo = JSON.createObjectNode();
        resumo.put("request_id", "req-1");
        resumo.put("lawsuits_count", total);
        return resumo;
    }

    @Test
    @DisplayName("resumo: grava a consulta com custo do contrato, total e quem pediu")
    void resumoGrava() {
        when(client.resumo(CNPJ)).thenReturn(resumoCom(57));

        service.consultarResumo("12.021.039/0001-74", null, analistaJudit);

        ArgumentCaptor<JuditConsultaEntity> salva = ArgumentCaptor.forClass(JuditConsultaEntity.class);
        verify(consultas).save(salva.capture());
        assertThat(salva.getValue().getServico()).isEqualTo(ServicoJudit.RESUMO);
        assertThat(salva.getValue().getCusto()).isEqualByComparingTo("0.75");
        assertThat(salva.getValue().getTotalProcessos()).isEqualTo(57);
        assertThat(salva.getValue().getRequestId()).isEqualTo("req-1");
        assertThat(salva.getValue().getUsuarioNome()).isEqualTo("Ana");
        verify(consultas).travarLimite(JuditService.TRAVA_LIMITE);
    }

    @Test
    @DisplayName("sem a marca Judit não consulta")
    void semMarca() {
        UserEntity analista = UserEntity.builder().id(UUID.randomUUID()).name("Bia").analista(true).build();

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, null, analista)).isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(client);
        verify(consultas, never()).save(any());
    }

    @Test
    @DisplayName("limite: gasto do mês mais a consulta acima de R$ 1.000 bloqueia sem chamar")
    void limiteBloqueia() {
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("999.50"));

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, null, analistaJudit))
                .isInstanceOf(LimiteJuditException.class)
                .hasMessageContaining("R$ 999,50")
                .hasMessageContaining("R$ 0,75");
        verifyNoInteractions(client);
        verify(consultas, never()).save(any());
    }

    @Test
    @DisplayName("limite: chegar exatamente em R$ 1.000 ainda passa")
    void limiteExato() {
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("999.25"));
        when(client.resumo(CNPJ)).thenReturn(resumoCom(3));

        service.consultarResumo(CNPJ, null, analistaJudit);

        verify(consultas).save(any());
    }

    @Test
    @DisplayName("resumo 404: empresa sem nada na Judit vira total zero e conta o custo")
    void resumoNaoEncontrado() {
        when(client.resumo(CNPJ)).thenThrow(new ExternalApiException(JuditClient.PROVIDER, 404, "nada", null));

        service.consultarResumo(CNPJ, null, analistaJudit);

        ArgumentCaptor<JuditConsultaEntity> salva = ArgumentCaptor.forClass(JuditConsultaEntity.class);
        verify(consultas).save(salva.capture());
        assertThat(salva.getValue().getTotalProcessos()).isZero();
        assertThat(salva.getValue().getCusto()).isEqualByComparingTo("0.75");
        assertThat(salva.getValue().getResposta().path("nao_encontrado").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("resumo: erro da Judit que não é 404 sobe e nada é gravado")
    void resumoErro() {
        when(client.resumo(CNPJ)).thenThrow(new ExternalApiException(JuditClient.PROVIDER, 502, "chave", null));

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, null, analistaJudit)).isInstanceOf(ExternalApiException.class);
        verify(consultas, never()).save(any());
    }

    // ── detalhe de processo ─────────────────────────────────────────────────────────────────────

    private static final String CNJ = "1009479-30.2024.8.26.0011";

    private void listaCom(String cnj) throws Exception {
        when(consultas.findFirstByDocumentoAndServicoOrderByCriadoEmDesc(CNPJ, ServicoJudit.PROCESSOS))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().documento(CNPJ).servico(ServicoJudit.PROCESSOS)
                        .custo(new BigDecimal("1.50")).criadoEm(LocalDateTime.now())
                        .resposta(JSON.readTree("{\"lawsuits\": [{\"code\": \"" + cnj + "\"}]}")).build()));
    }

    @Test
    @DisplayName("detalhar: cobra o preço do detalhe e grava PENDENTE com o request — o acompanhador acha pelo banco")
    void detalharPede() throws Exception {
        listaCom(CNJ);
        when(consultas.findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(CNPJ, CNJ, ServicoJudit.DETALHE))
                .thenReturn(Optional.empty());
        when(client.pedirDetalhe(CNJ)).thenReturn("req-9");
        when(consultas.save(any())).thenAnswer(inv -> {
            JuditConsultaEntity salva = inv.getArgument(0);
            salva.setId(UUID.randomUUID());
            return salva;
        });

        JuditService.DetalheConsultado detalhe = service.detalhar(CNPJ, CNJ, "I G LABEGALINI", analistaJudit);

        assertThat(detalhe.situacao()).isEqualTo(SituacaoConsultaJudit.PENDENTE);
        ArgumentCaptor<JuditConsultaEntity> salva = ArgumentCaptor.forClass(JuditConsultaEntity.class);
        verify(consultas).save(salva.capture());
        assertThat(salva.getValue().getServico()).isEqualTo(ServicoJudit.DETALHE);
        assertThat(salva.getValue().getCusto()).isEqualByComparingTo("0.35");
        assertThat(salva.getValue().getCnj()).isEqualTo(CNJ);
        assertThat(salva.getValue().getRequestId()).isEqualTo("req-9");
        assertThat(salva.getValue().getResposta()).isNull();
        assertThat(salva.getValue().getNomeEmpresa()).isEqualTo("I G LABEGALINI");
        assertThat(salva.getValue().getUsuarioId()).isEqualTo(analistaJudit.getId());
        verify(eventos, never()).publishEvent(any());
        verify(consultas).travarLimite(JuditService.TRAVA_LIMITE);
    }

    @Test
    @DisplayName("detalhar: com um pedido ainda pendente, devolve ele sem cobrar de novo")
    void detalharPendenteNaoCobra() throws Exception {
        listaCom(CNJ);
        when(consultas.findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(CNPJ, CNJ, ServicoJudit.DETALHE))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().cnj(CNJ).servico(ServicoJudit.DETALHE)
                        .situacao(SituacaoConsultaJudit.PENDENTE).custo(new BigDecimal("0.35"))
                        .criadoEm(LocalDateTime.now()).build()));

        service.detalhar(CNPJ, CNJ, null, analistaJudit);

        verifyNoInteractions(client);
        verify(consultas, never()).save(any());
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    @DisplayName("detalhar: processo fora da lista da empresa, CNJ inválido ou sem a marca são recusados")
    void detalharRecusa() throws Exception {
        listaCom("0000001-00.2024.8.26.0001");

        assertThatThrownBy(() -> service.detalhar(CNPJ, CNJ, null, analistaJudit))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("não está na lista");
        assertThatThrownBy(() -> service.detalhar(CNPJ, "123", null, analistaJudit))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20 dígitos");
        UserEntity semMarca = UserEntity.builder().id(UUID.randomUUID()).name("Bia").analista(true).build();
        assertThatThrownBy(() -> service.detalhar(CNPJ, CNJ, null, semMarca)).isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("detalhar: o limite do mês vale para o detalhe também")
    void detalharLimite() throws Exception {
        listaCom(CNJ);
        when(consultas.findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(CNPJ, CNJ, ServicoJudit.DETALHE))
                .thenReturn(Optional.empty());
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("999.80"));

        assertThatThrownBy(() -> service.detalhar(CNPJ, CNJ, null, analistaJudit)).isInstanceOf(LimiteJuditException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("concluir: grava a resposta só se ainda estava pendente; a segunda vez devolve falso")
    void concluirUmaVez() throws Exception {
        UUID id = UUID.randomUUID();
        JuditConsultaEntity pendente = JuditConsultaEntity.builder().id(id).situacao(SituacaoConsultaJudit.PENDENTE).build();
        when(consultas.travar(id)).thenReturn(Optional.of(pendente));

        assertThat(service.concluirDetalhe(id, JSON.readTree("{\"page_data\": []}"))).isTrue();
        assertThat(pendente.getSituacao()).isEqualTo(SituacaoConsultaJudit.CONCLUIDA);
        assertThat(pendente.getConcluidaEm()).isNotNull();

        assertThat(service.concluirDetalhe(id, JSON.readTree("{}"))).isFalse();
        assertThat(service.falhar(id, "x")).isFalse();
    }

    // ── franquia e liberação ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("limite: o valor liberado pelo admin no mês soma ao limite")
    void liberacaoSomaAoLimite() {
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("999.50"));
        when(liberacoes.somarDoMes(any())).thenReturn(new BigDecimal("100"));
        when(client.resumo(CNPJ)).thenReturn(resumoCom(3));

        service.consultarResumo(CNPJ, null, analistaJudit);

        verify(consultas).save(any());
    }

    @Test
    @DisplayName("limite: a mensagem diz o que fazer — pedir ao admin")
    void limiteMandaPedirAoAdmin() {
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("999.50"));

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, null, analistaJudit))
                .isInstanceOf(LimiteJuditException.class)
                .hasMessageContaining("admin");
    }

    // ── On Demand ───────────────────────────────────────────────────────────────────────────────

    private ObjectNode resumoComPolos(int reu, int autor) {
        ObjectNode resumo = resumoCom(reu + autor);
        resumo.putArray("sides")
                .add(JSON.createObjectNode().put("value", "PASSIVE").put("count", reu))
                .add(JSON.createObjectNode().put("value", "ACTIVE").put("count", autor));
        return resumo;
    }

    private UUID preConsultaGuardada(int reu, int autor, LocalDateTime quando) {
        UUID id = UUID.randomUUID();
        when(consultas.findFirstByDocumentoAndServicoOrderByCriadoEmDesc(CNPJ, ServicoJudit.RESUMO))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().id(id).documento(CNPJ).servico(ServicoJudit.RESUMO)
                        .custo(new BigDecimal("0.75")).resposta(resumoComPolos(reu, autor)).criadoEm(quando).build()));
        return id;
    }

    private JuditConsultaEntity onDemandSalvo() {
        ArgumentCaptor<JuditConsultaEntity> salva = ArgumentCaptor.forClass(JuditConsultaEntity.class);
        verify(consultas).save(salva.capture());
        return salva.getValue();
    }

    @Test
    @DisplayName("On Demand direto: estima um bloco, grava PENDENTE como ré e sem pré-consulta")
    void onDemandSemPreConsulta() {
        when(client.pedirOnDemand(CNPJ, PoloJudit.PASSIVO)).thenReturn("req-od");

        service.consultarOnDemand(CNPJ, PoloJudit.PASSIVO, "IG LABEGALINI", analistaJudit);

        JuditConsultaEntity salva = onDemandSalvo();
        assertThat(salva.getServico()).isEqualTo(ServicoJudit.ON_DEMAND);
        assertThat(salva.getPolo()).isEqualTo(PoloJudit.PASSIVO);
        assertThat(salva.getSituacao()).isEqualTo(SituacaoConsultaJudit.PENDENTE);
        assertThat(salva.getCusto()).isEqualByComparingTo("6.00");
        assertThat(salva.getCustoEstimado()).isEqualByComparingTo("6.00");
        assertThat(salva.getPreConsultaId()).isNull();
        assertThat(salva.getNomeEmpresa()).isEqualTo("IG LABEGALINI");
        assertThat(salva.getRequestId()).isEqualTo("req-od");
        verify(consultas).travarLimite(JuditService.TRAVA_LIMITE);
    }

    @Test
    @DisplayName("On Demand com pré-consulta: estima pelos processos do polo e guarda qual pré-consulta usou")
    void onDemandComPreConsulta() {
        UUID pre = preConsultaGuardada(1840, 470, LocalDateTime.now().minusDays(2));
        when(client.pedirOnDemand(CNPJ, PoloJudit.PASSIVO)).thenReturn("req-od");

        service.consultarOnDemand(CNPJ, PoloJudit.PASSIVO, null, analistaJudit);

        JuditConsultaEntity salva = onDemandSalvo();
        assertThat(salva.getCustoEstimado()).isEqualByComparingTo("12.00");
        assertThat(salva.getPreConsultaId()).isEqualTo(pre);
    }

    @Test
    @DisplayName("On Demand: pré-consulta vencida não serve de estimativa")
    void onDemandPreConsultaVencida() {
        preConsultaGuardada(1840, 0, LocalDateTime.now().minusDays(31));
        when(client.pedirOnDemand(CNPJ, PoloJudit.PASSIVO)).thenReturn("req-od");

        service.consultarOnDemand(CNPJ, PoloJudit.PASSIVO, null, analistaJudit);

        JuditConsultaEntity salva = onDemandSalvo();
        assertThat(salva.getCustoEstimado()).isEqualByComparingTo("6.00");
        assertThat(salva.getPreConsultaId()).isNull();
    }

    @Test
    @DisplayName("On Demand: o mesmo polo ainda pendente não cobra de novo")
    void onDemandPendenteNaoCobra() {
        when(consultas.findFirstByDocumentoAndServicoAndPoloOrderByCriadoEmDesc(CNPJ, ServicoJudit.ON_DEMAND, PoloJudit.PASSIVO))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().servico(ServicoJudit.ON_DEMAND).polo(PoloJudit.PASSIVO)
                        .situacao(SituacaoConsultaJudit.PENDENTE).custo(new BigDecimal("6.00"))
                        .criadoEm(LocalDateTime.now()).build()));

        service.consultarOnDemand(CNPJ, PoloJudit.PASSIVO, null, analistaJudit);

        verifyNoInteractions(client);
        verify(consultas, never()).save(any());
    }

    @Test
    @DisplayName("On Demand: a trava confere com a estimativa, antes de chamar")
    void onDemandLimite() {
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("995"));

        assertThatThrownBy(() -> service.consultarOnDemand(CNPJ, PoloJudit.PASSIVO, null, analistaJudit))
                .isInstanceOf(LimiteJuditException.class)
                .hasMessageContaining("R$ 6,00");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("On Demand sem polo ou sem a marca Judit é recusado")
    void onDemandRecusa() {
        UserEntity semMarca = UserEntity.builder().id(UUID.randomUUID()).name("Bia").analista(true).build();

        assertThatThrownBy(() -> service.consultarOnDemand(CNPJ, null, null, analistaJudit))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.consultarOnDemand(CNPJ, PoloJudit.PASSIVO, null, semMarca))
                .isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("concluir On Demand: grava a lista no formato do Data Lake e troca a estimativa pelo custo real")
    void concluirOnDemand() throws Exception {
        UUID id = UUID.randomUUID();
        JuditConsultaEntity pendente = JuditConsultaEntity.builder().id(id).documento(CNPJ).servico(ServicoJudit.ON_DEMAND)
                .polo(PoloJudit.PASSIVO).situacao(SituacaoConsultaJudit.PENDENTE).requestId("req-od")
                .custo(new BigDecimal("6.00")).custoEstimado(new BigDecimal("6.00")).build();
        when(consultas.travar(id)).thenReturn(Optional.of(pendente));
        JsonNode respostas = JSON.readTree("""
                {"all_count": 1500, "page_data": [
                  {"response_type": "lawsuit", "response_data": {"code": "A", "instance": 1}},
                  {"response_type": "lawsuit", "response_data": {"code": "A", "instance": 2}},
                  {"response_type": "lawsuit", "response_data": {"code": "B", "instance": 1}},
                  {"response_type": "application_info", "response_data": {}}]}""");

        JuditService.OnDemandConcluido concluido = service.concluirOnDemand(id, respostas);

        assertThat(concluido.registros()).isEqualTo(1500);
        assertThat(concluido.processos()).isEqualTo(2);
        assertThat(concluido.custo()).isEqualByComparingTo("12.00");
        assertThat(pendente.getSituacao()).isEqualTo(SituacaoConsultaJudit.CONCLUIDA);
        assertThat(pendente.getCusto()).isEqualByComparingTo("12.00");
        assertThat(pendente.getCustoEstimado()).isEqualByComparingTo("6.00");
        assertThat(pendente.getTotalProcessos()).isEqualTo(1500);
        assertThat(pendente.getResposta().path("lawsuits")).hasSize(3);
        assertThat(pendente.getResposta().path("_portal").path("polo").asText()).isEqualTo("PASSIVO");

        assertThat(service.concluirOnDemand(id, respostas)).isNull();
    }

    @Test
    @DisplayName("estimativa: mínimo um bloco, um a cada 1.000, e aviso perto da virada")
    void estimativa() {
        assertThat(service.estimativa(null)).isEqualTo(new JuditService.Estimativa(null, 1, new BigDecimal("6.00"), false));
        assertThat(service.estimativa(0).blocos()).isEqualTo(1);
        assertThat(service.estimativa(0).podeVirarBloco()).isFalse();
        assertThat(service.estimativa(33).podeVirarBloco()).isFalse();
        assertThat(service.estimativa(950).podeVirarBloco()).isTrue();
        assertThat(service.estimativa(1000).blocos()).isEqualTo(1);
        assertThat(service.estimativa(1000).podeVirarBloco()).isTrue();
        assertThat(service.estimativa(1840).custo()).isEqualByComparingTo("12.00");
    }

    @Test
    @DisplayName("lista: junta o último On Demand concluído de ré e de autora, sem repetir registro")
    void listaUnificada() throws Exception {
        String cnpjParte = "{\"main_document\": \"" + CNPJ + "\", \"side\": \"%s\"}";
        JsonNode reu = JSON.readTree("{\"lawsuits\": [{\"code\": \"A\", \"instance\": 1, \"parties\": ["
                + cnpjParte.formatted("Passive") + "]}]}");
        JsonNode autor = JSON.readTree("{\"lawsuits\": [{\"code\": \"A\", \"instance\": 1, \"parties\": ["
                + cnpjParte.formatted("Passive") + "]}, {\"code\": \"B\", \"instance\": 1, \"parties\": ["
                + cnpjParte.formatted("Active") + "]}]}");
        when(consultas.findFirstByDocumentoAndServicoAndPoloAndSituacaoOrderByCriadoEmDesc(
                CNPJ, ServicoJudit.ON_DEMAND, PoloJudit.PASSIVO, SituacaoConsultaJudit.CONCLUIDA))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().polo(PoloJudit.PASSIVO).resposta(reu)
                        .custo(new BigDecimal("6.00")).criadoEm(LocalDateTime.now().minusHours(1)).usuarioNome("Ana").build()));
        when(consultas.findFirstByDocumentoAndServicoAndPoloAndSituacaoOrderByCriadoEmDesc(
                CNPJ, ServicoJudit.ON_DEMAND, PoloJudit.ATIVO, SituacaoConsultaJudit.CONCLUIDA))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().polo(PoloJudit.ATIVO).resposta(autor)
                        .custo(new BigDecimal("6.00")).criadoEm(LocalDateTime.now()).usuarioNome("Bia").build()));

        JuditService.ProcessosConsultados processos = service.processos(CNPJ);

        assertThat(processos.onDemand()).isTrue();
        assertThat(processos.somenteReu()).isFalse();
        assertThat(processos.consultadoPor()).isEqualTo("Bia");
        assertThat(processos.custo()).isEqualByComparingTo("12.00");
        assertThat(processos.dados().registros()).isEqualTo(2);
        assertThat(processos.dados().comoReu()).isEqualTo(1);
        assertThat(processos.dados().comoAutor()).isEqualTo(1);
    }
}
