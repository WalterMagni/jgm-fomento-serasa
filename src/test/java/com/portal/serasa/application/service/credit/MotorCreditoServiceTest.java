package com.portal.serasa.application.service.credit;

import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.credit.DadosAnalise;
import com.portal.serasa.domain.model.credit.PerfilCredito;
import com.portal.serasa.domain.model.credit.ResultadoAnaliseCredito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes unitários para MotorCreditoService.
 * Valida as regras de negócio dos 10 perfis de crédito.
 */
@ExtendWith(MockitoExtension.class)
class MotorCreditoServiceTest {

    @Mock
    private CreditScoreExtractor extractor;

    @InjectMocks
    private MotorCreditoService motorCreditoService;

    private CreditAnalysis analysis;

    @BeforeEach
    void setUp() {
        analysis = CreditAnalysis.builder()
                .cnpj("12345678000195")
                .companyName("Empresa Teste LTDA")
                .build();
    }

    @Test
    @DisplayName("REJEICAO: deve reprovar quando score HPJ8 é crítico (< 200)")
    void shouldReturnREJEICAO_whenScoreIsCritical() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(150)
                .totalPendencias(0)
                .totalValorDividas(BigDecimal.ZERO)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.REJEICAO);
        assertThat(resultado.getPerfil().isReprovado()).isTrue();
        assertThat(resultado.getDecisao()).isEqualTo("REPROVADO");
    }

    @Test
    @DisplayName("REJEICAO: deve reprovar quando total de dívidas supera R$ 2.000")
    void shouldReturnREJEICAO_whenDividasExceedThreshold() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(500)
                .totalPendencias(1)
                .totalValorDividas(new BigDecimal("5000.00"))
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.REJEICAO);
    }

    @Test
    @DisplayName("RISCO_SOCIO: deve exigir garantia quando sócio tem restrição")
    void shouldReturnRISCO_SOCIO_whenPartnerHasRestriction() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(600)
                .possuiSocioComRestricao(true)
                .socioComRestricaoNome("João da Silva")
                .totalValorDividas(BigDecimal.ZERO)
                .totalPendencias(0)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.RISCO_SOCIO);
        assertThat(resultado.getPerfil().exigeAnaliseManual()).isTrue();
    }

    @Test
    @DisplayName("RISCO_JURIDICO: score bom mas processo judicial oculto (HPJ8 > 600 e HSJ6 < 450)")
    void shouldReturnRISCO_JURIDICO_whenCreditGoodButLegalRiskHigh() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(700)
                .scoreHSJ6(300)       // HSJ6 < 450 = risco jurídico
                .totalValorDividas(BigDecimal.ZERO)
                .totalPendencias(0)
                .possuiSocioComRestricao(false)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.RISCO_JURIDICO);
        assertThat(resultado.getDecisao()).isEqualTo("REVISÃO MANUAL");
    }

    @Test
    @DisplayName("FOME_CREDITO: muitas consultas recentes com score médio-baixo")
    void shouldReturnFOME_CREDITO_whenTooManyRecentInquiries() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(650)
                .scoreHSJ6(600)       // HSJ6 >= 450 => não cai em RISCO_JURIDICO
                .consultasUltimos30Dias(8)
                .totalValorDividas(BigDecimal.ZERO)
                .totalPendencias(0)
                .possuiSocioComRestricao(false)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.FOME_CREDITO);
        assertThat(resultado.getDecisao()).isEqualTo("VENDA À VISTA");
    }

    @Test
    @DisplayName("RECUPERACAO: empresa com score baixo (< 400)")
    void shouldReturnRECUPERACAO_whenScoreIsLow() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(350)
                .totalValorDividas(BigDecimal.ZERO)
                .totalPendencias(0)
                .possuiSocioComRestricao(false)
                .consultasUltimos30Dias(0)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.RECUPERACAO);
    }

    @Test
    @DisplayName("STARTUP: empresa nova sem negativos deve receber perfil STARTUP")
    void shouldReturnSTARTUP_whenCompanyIsNewAndClean() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(500)
                .scoreHSJ6(600)
                .idadeMeses(12)       // < 24 meses
                .totalPendencias(0)
                .totalValorDividas(BigDecimal.ZERO)
                .possuiSocioComRestricao(false)
                .consultasUltimos30Dias(2)
                .scoreHIP2(70)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.STARTUP);
        assertThat(resultado.getPerfil().isAprovadoComRestricao()).isTrue();
    }

    @Test
    @DisplayName("PRIME: excelente pagador com score HPJ8 > 750 e HSJ6 > 800")
    void shouldReturnPRIME_whenAllScoresAreHigh() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(900)
                .scoreHSJ6(900)
                .scoreHIP2(95)
                .idadeMeses(120)
                .totalPendencias(0)
                .totalValorDividas(BigDecimal.ZERO)
                .possuiSocioComRestricao(false)
                .consultasUltimos30Dias(1)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.PRIME);
        assertThat(resultado.getPerfil().isAprovado()).isTrue();
        assertThat(resultado.getDecisao()).isEqualTo("APROVAÇÃO AUTOMÁTICA");
    }

    @Test
    @DisplayName("STANDARD: empresa saudável sem critérios especiais cai no perfil padrão")
    void shouldReturnSTANDARD_asSafeDefault() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(600)
                .scoreHSJ6(600)
                .scoreHIP2(75)
                .idadeMeses(60)
                .totalPendencias(0)
                .totalValorDividas(BigDecimal.ZERO)
                .possuiSocioComRestricao(false)
                .consultasUltimos30Dias(2)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getPerfil()).isEqualTo(PerfilCredito.STANDARD);
        assertThat(resultado.getPerfil().isAprovado()).isTrue();
    }

    @Test
    @DisplayName("analisar: resultado deve sempre ter cnpj, decisao e mensagem preenchidos")
    void shouldAlwaysPopulateMandatoryFields() {
        DadosAnalise dados = baseDados()
                .scoreHPJ8(600)
                .scoreHSJ6(600)
                .totalValorDividas(BigDecimal.ZERO)
                .totalPendencias(0)
                .possuiSocioComRestricao(false)
                .consultasUltimos30Dias(0)
                .build();
        when(extractor.extrair(any(), any())).thenReturn(dados);

        ResultadoAnaliseCredito resultado = motorCreditoService.analisar(analysis, Optional.empty());

        assertThat(resultado.getCnpj()).isEqualTo("12345678000195");
        assertThat(resultado.getDecisao()).isNotBlank();
        assertThat(resultado.getMensagem()).isNotBlank();
        assertThat(resultado.getCorHex()).isNotBlank();
        assertThat(resultado.getDataAnalise()).isNotNull();
    }

    @Test
    @DisplayName("PerfilCredito: todos os 10 perfis devem ter campos não nulos")
    void allPerfilCreditoValues_shouldHaveNonNullFields() {
        for (PerfilCredito perfil : PerfilCredito.values()) {
            assertThat(perfil.getDecisao()).isNotBlank();
            assertThat(perfil.getTitulo()).isNotBlank();
            assertThat(perfil.getCorHex()).startsWith("#");
            assertThat(perfil.getMensagemPadrao()).isNotBlank();
            assertThat(perfil.getPrioridade()).isGreaterThan(0);
        }
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private DadosAnalise.DadosAnaliseBuilder baseDados() {
        return DadosAnalise.builder()
                .scoreHPJ8(null)
                .scoreHSJ6(null)
                .scoreHIP2(null)
                .scoreHFE3(null)
                .scoreHLO4(null)
                .totalPendencias(0)
                .totalValorDividas(BigDecimal.ZERO)
                .possuiSocioComRestricao(false)
                .consultasUltimos30Dias(0)
                .consultasUltimos90Dias(0)
                .consultasTotal(0)
                .idadeMeses(60);
    }
}
