package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoEntradaAutomaticaTest {

    @Mock private ProspeccaoService prospeccaoService;
    @Mock private CreditAnalysisRepository creditAnalysisRepository;

    @InjectMocks private ProspeccaoEntradaAutomatica entrada;

    private CreditAnalysis analise(String visaoCedente, String nome) {
        return CreditAnalysis.builder()
                .id(42L)
                .cnpj("11222333000181")
                .companyName(nome)
                .visaoCedente(visaoCedente)
                .build();
    }

    @Test
    @DisplayName("visão cedente SIM abre card em triagem")
    void shouldOpenCardWhenAssignorProfileDetected() {
        when(prospeccaoService.criarPorAnalise(any(), any(), any()))
                .thenReturn(ProspeccaoEntity.builder().id(UUID.randomUUID()).build());

        entrada.aoSalvarAnalise(analise("SIM", "ACME LTDA"));

        verify(prospeccaoService).criarPorAnalise("11222333000181", "ACME LTDA", 42L);
    }

    @Test
    @DisplayName("visão cedente NAO ou PENDENTE não abre nada")
    void shouldIgnoreNonAssignor() {
        entrada.aoSalvarAnalise(analise("NAO", "ACME LTDA"));
        entrada.aoSalvarAnalise(analise("PENDENTE", "ACME LTDA"));
        entrada.aoSalvarAnalise(null);

        verify(prospeccaoService, never()).criarPorAnalise(any(), any(), any());
    }

    @Test
    @DisplayName("sem razão social, o CNPJ vira o nome do card em vez de nulo")
    void shouldFallBackToCnpjAsName() {
        entrada.aoSalvarAnalise(analise("SIM", null));

        verify(prospeccaoService).criarPorAnalise("11222333000181", "11222333000181", 42L);
    }

    @Test
    @DisplayName("falha ao abrir card não derruba o enriquecimento — a consulta ao Serasa é paga")
    void shouldSwallowFailureToProtectPaidQuery() {
        when(prospeccaoService.criarPorAnalise(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("CNPJ deve ter 14 dígitos"));

        // Sem exceção propagada: perder o card é recuperável pelo backfill, perder a análise não.
        entrada.aoSalvarAnalise(analise("SIM", "ACME LTDA"));
    }

    @Test
    @DisplayName("backfill conta só os cards que realmente criou")
    void shouldCountOnlyCreatedCards() {
        CreditAnalysis comCard = analise("SIM", "JA TEM CARD");
        CreditAnalysis semCard = CreditAnalysis.builder()
                .id(43L).cnpj("99888777000166").companyName("NOVA").visaoCedente("SIM").build();
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(List.of(comCard, semCard));
        // criarPorAnalise devolve null quando já existe card aberto para o CNPJ.
        when(prospeccaoService.criarPorAnalise(eq("11222333000181"), any(), any())).thenReturn(null);
        when(prospeccaoService.criarPorAnalise(eq("99888777000166"), any(), any()))
                .thenReturn(ProspeccaoEntity.builder().id(UUID.randomUUID()).build());

        assertThat(entrada.backfill(null, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("backfill segue o lote quando uma análise tem CNPJ inválido")
    void shouldKeepGoingAfterBadRow() {
        CreditAnalysis ruim = CreditAnalysis.builder()
                .id(1L).cnpj("123").companyName("QUEBRADA").visaoCedente("SIM").build();
        CreditAnalysis boa = CreditAnalysis.builder()
                .id(2L).cnpj("99888777000166").companyName("BOA").visaoCedente("SIM").build();
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(List.of(ruim, boa));
        when(prospeccaoService.criarPorAnalise(eq("123"), any(), any()))
                .thenThrow(new IllegalArgumentException("CNPJ deve ter 14 dígitos: 123"));
        when(prospeccaoService.criarPorAnalise(eq("99888777000166"), any(), any()))
                .thenReturn(ProspeccaoEntity.builder().id(UUID.randomUUID()).build());

        assertThat(entrada.backfill(null, 0)).isEqualTo(1);
        verify(prospeccaoService, times(2)).criarPorAnalise(any(), any(), any());
    }

    @Test
    @DisplayName("backfill respeita o teto: produção tem centenas de análises com visão cedente")
    void shouldCapTheBatch() {
        List<CreditAnalysis> muitas = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            muitas.add(CreditAnalysis.builder()
                    .id((long) i)
                    .cnpj(String.format("%014d", 11222333000181L + i))
                    .companyName("EMPRESA " + i)
                    .visaoCedente("SIM")
                    .build());
        }
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(muitas);
        when(prospeccaoService.criarPorAnalise(any(), any(), any()))
                .thenReturn(ProspeccaoEntity.builder().id(UUID.randomUUID()).build());

        assertThat(entrada.backfill(null, 10)).isEqualTo(10);
        verify(prospeccaoService, times(10)).criarPorAnalise(any(), any(), any());
    }

    @Test
    @DisplayName("backfill sem limite informado usa o teto padrão em vez de trazer tudo")
    void shouldFallBackToDefaultCap() {
        List<CreditAnalysis> muitas = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) {
            muitas.add(CreditAnalysis.builder()
                    .id((long) i)
                    .cnpj(String.format("%014d", 11222333000181L + i))
                    .companyName("EMPRESA " + i)
                    .visaoCedente("SIM")
                    .build());
        }
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(muitas);
        when(prospeccaoService.criarPorAnalise(any(), any(), any()))
                .thenReturn(ProspeccaoEntity.builder().id(UUID.randomUUID()).build());

        assertThat(entrada.backfill(null, 0)).isEqualTo(ProspeccaoEntradaAutomatica.LIMITE_PADRAO);
    }

    @Test
    @DisplayName("backfill recorta por data de consulta")
    void shouldFilterByDate() {
        CreditAnalysis antiga = CreditAnalysis.builder().id(1L).cnpj("11222333000181")
                .companyName("ANTIGA").visaoCedente("SIM")
                .consultaEm(LocalDateTime.now().minusYears(2)).build();
        CreditAnalysis recente = CreditAnalysis.builder().id(2L).cnpj("99888777000166")
                .companyName("RECENTE").visaoCedente("SIM")
                .consultaEm(LocalDateTime.now().minusDays(3)).build();
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(List.of(antiga, recente));
        when(prospeccaoService.criarPorAnalise(any(), any(), any()))
                .thenReturn(ProspeccaoEntity.builder().id(UUID.randomUUID()).build());

        assertThat(entrada.backfill(LocalDate.now().minusDays(30), 100)).isEqualTo(1);
        verify(prospeccaoService).criarPorAnalise(eq("99888777000166"), any(), any());
    }

    @Test
    @DisplayName("prévia conta sem criar nada — a ação não tem desfazer em massa")
    void shouldPreviewWithoutCreating() {
        CreditAnalysis jaNaEsteira = CreditAnalysis.builder().id(1L).cnpj("11222333000181")
                .companyName("JA TEM").visaoCedente("SIM").build();
        CreditAnalysis nova = CreditAnalysis.builder().id(2L).cnpj("99888777000166")
                .companyName("NOVA").visaoCedente("SIM").build();
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(List.of(jaNaEsteira, nova));
        when(prospeccaoService.cardAbertoDoCnpj("11222333000181"))
                .thenReturn(java.util.Optional.of(ProspeccaoEntity.builder().id(UUID.randomUUID()).build()));
        when(prospeccaoService.cardAbertoDoCnpj("99888777000166")).thenReturn(java.util.Optional.empty());
        when(prospeccaoService.jaVeioDaAnalise(2L)).thenReturn(false);

        ProspeccaoEntradaAutomatica.Previa previa = entrada.previa(null);

        assertThat(previa.comVisaoCedente()).isEqualTo(2);
        assertThat(previa.jaNaEsteira()).isEqualTo(1);
        assertThat(previa.seriamCriados()).isEqualTo(1);
        assertThat(previa.amostra()).containsExactly("NOVA");
        verify(prospeccaoService, never()).criarPorAnalise(any(), any(), any());
    }

    @Test
    @DisplayName("backfill sem candidata não cria nada")
    void shouldHandleEmptyBackfill() {
        when(creditAnalysisRepository.findLatestByVisaoCedente("SIM")).thenReturn(List.of());

        assertThat(entrada.backfill(null, 0)).isZero();
    }
}
