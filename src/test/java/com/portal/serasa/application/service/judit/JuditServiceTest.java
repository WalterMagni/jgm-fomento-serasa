package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.exception.LimiteJuditException;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import com.portal.serasa.infrastructure.integration.judit.JuditClient;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import com.portal.serasa.infrastructure.persistence.entity.JuditConsultaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.JuditConsultaJpaRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Travas antes de cada chamada cobrada (marca Judit, limite do mês) e o custo registrado. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JuditServiceTest {

    private static final String CNPJ = "12021039000174";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock private JuditClient client;
    @Mock private JuditConsultaJpaRepository consultas;
    @Mock private ApplicationEventPublisher eventos;

    private JuditService service;
    private UserEntity analistaJudit;

    @BeforeEach
    void setUp() {
        JuditProperties properties = new JuditProperties("chave", "https://lawsuits.example", "https://requests.example",
                new BigDecimal("0.75"), new BigDecimal("1.50"), new BigDecimal("0.35"),
                new BigDecimal("900"), new BigDecimal("1000"), 30, 0, 1);
        service = new JuditService(client, properties, consultas, JSON, eventos);
        analistaJudit = UserEntity.builder().id(UUID.randomUUID()).name("Ana").email("ana@jgm.com.br")
                .analista(true).judit(true).build();
        when(consultas.somarCustoDesde(any())).thenReturn(BigDecimal.ZERO);
        when(consultas.findFirstByDocumentoAndServicoOrderByCriadoEmDesc(anyString(), any())).thenReturn(Optional.empty());
    }

    private ObjectNode resumoCom(int total) {
        ObjectNode resumo = JSON.createObjectNode();
        resumo.put("request_id", "req-1");
        resumo.put("lawsuits_count", total);
        return resumo;
    }

    private void resumoGuardado(int total) {
        when(consultas.findFirstByDocumentoAndServicoOrderByCriadoEmDesc(CNPJ, ServicoJudit.RESUMO))
                .thenReturn(Optional.of(JuditConsultaEntity.builder().documento(CNPJ).servico(ServicoJudit.RESUMO)
                        .custo(new BigDecimal("0.75")).resposta(resumoCom(total)).criadoEm(LocalDateTime.now()).build()));
    }

    @Test
    @DisplayName("resumo: grava a consulta com custo do contrato, total e quem pediu")
    void resumoGrava() {
        when(client.resumo(CNPJ)).thenReturn(resumoCom(57));

        service.consultarResumo("12.021.039/0001-74", analistaJudit);

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

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, analista)).isInstanceOf(AcessoNegadoException.class);
        verifyNoInteractions(client);
        verify(consultas, never()).save(any());
    }

    @Test
    @DisplayName("limite: gasto do mês mais a consulta acima de R$ 1.000 bloqueia sem chamar")
    void limiteBloqueia() {
        when(consultas.somarCustoDesde(any())).thenReturn(new BigDecimal("999.50"));

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, analistaJudit))
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

        service.consultarResumo(CNPJ, analistaJudit);

        verify(consultas).save(any());
    }

    @Test
    @DisplayName("resumo 404: empresa sem nada na Judit vira total zero e conta o custo")
    void resumoNaoEncontrado() {
        when(client.resumo(CNPJ)).thenThrow(new ExternalApiException(JuditClient.PROVIDER, 404, "nada", null));

        service.consultarResumo(CNPJ, analistaJudit);

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

        assertThatThrownBy(() -> service.consultarResumo(CNPJ, analistaJudit)).isInstanceOf(ExternalApiException.class);
        verify(consultas, never()).save(any());
    }

    @Test
    @DisplayName("processos: exige resumo antes e recusa quando o resumo deu zero")
    void processosExigeResumo() {

        assertThatThrownBy(() -> service.consultarProcessos(CNPJ, analistaJudit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumo");

        resumoGuardado(0);
        assertThatThrownBy(() -> service.consultarProcessos(CNPJ, analistaJudit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não encontrou");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("processos: até 1.000 traz todos os polos e custa um bloco")
    void processosTodos() throws Exception {
        resumoGuardado(57);
        when(client.processos(eq(CNPJ), isNull()))
                .thenReturn(JSON.readTree("{\"lawsuits\": [{\"code\": \"A\"}, {\"code\": \"B\"}]}"));

        service.consultarProcessos(CNPJ, analistaJudit);

        ArgumentCaptor<JuditConsultaEntity> salva = ArgumentCaptor.forClass(JuditConsultaEntity.class);
        verify(consultas).save(salva.capture());
        assertThat(salva.getValue().getServico()).isEqualTo(ServicoJudit.PROCESSOS);
        assertThat(salva.getValue().getCusto()).isEqualByComparingTo("1.50");
        assertThat(salva.getValue().getTotalProcessos()).isEqualTo(2);
        assertThat(salva.getValue().getResposta().path("_portal").path("somente_reu").asBoolean()).isFalse();
        assertThat(salva.getValue().getResposta().path("_portal").path("total_no_resumo").asInt()).isEqualTo(57);
    }

    @Test
    @DisplayName("processos: acima de 1.000 no resumo pede só os de réu")
    void processosSomenteReu() throws Exception {
        resumoGuardado(1199);
        when(client.processos(CNPJ, "Passive")).thenReturn(JSON.readTree("{\"lawsuits\": []}"));

        service.consultarProcessos(CNPJ, analistaJudit);

        verify(client).processos(CNPJ, "Passive");
    }

    @Test
    @DisplayName("custo da lista: um bloco por 1.000 registros, mínimo de um")
    void custoPorBloco() {
        assertThat(service.custoProcessos(0)).isEqualByComparingTo("1.50");
        assertThat(service.custoProcessos(1000)).isEqualByComparingTo("1.50");
        assertThat(service.custoProcessos(1001)).isEqualByComparingTo("3.00");
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
    @DisplayName("detalhar: cobra o preço do detalhe, grava PENDENTE com o request e avisa o acompanhador")
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
        ArgumentCaptor<JuditService.DetalhePedido> pedido = ArgumentCaptor.forClass(JuditService.DetalhePedido.class);
        verify(eventos).publishEvent(pedido.capture());
        assertThat(pedido.getValue().nomeEmpresa()).isEqualTo("I G LABEGALINI");
        assertThat(pedido.getValue().usuarioId()).isEqualTo(analistaJudit.getId());
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
        assertThat(service.falharDetalhe(id, "x")).isFalse();
    }
}
