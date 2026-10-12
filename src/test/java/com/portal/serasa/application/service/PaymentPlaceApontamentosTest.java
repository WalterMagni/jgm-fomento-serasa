package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Visão Cedente em lote, usada pela esteira de liberação: mesma regra da página da empresa. */
@ExtendWith(MockitoExtension.class)
class PaymentPlaceApontamentosTest {

    private static final String CEDENTE = "07741967000173";
    private static final String SACADO = "58277872000939";
    private static final String OUTRO = "11222333000181";

    @Mock private PaymentPlaceEntryJpaRepository entryRepository;
    @InjectMocks private PaymentPlaceAnalysisService service;

    private static PaymentPlaceEntryEntity entrada(String cedente, String sacado, String pago) {
        PaymentPlaceEntryEntity entrada = new PaymentPlaceEntryEntity();
        entrada.setClientDocument(cedente);
        entrada.setPayerDocument(sacado);
        entrada.setPaidValue(pago);
        entrada.setAnalystDecision("CEDENTE");
        return entrada;
    }

    @Test
    @DisplayName("soma títulos e valor pago por documento, contando o documento como cedente ou como sacado")
    void shouldSumPerDocumentAsCedenteOrSacado() {
        when(entryRepository.findCedenteDecidedForCompanies(any())).thenReturn(List.of(
                entrada(CEDENTE, "58.277.872/0009-39", "3.025,40"),
                entrada(CEDENTE, "22.675.768/0001-06", "9.022,65"),
                entrada("99999999000191", "58.277.872/0009-39", "1.000,00")));

        Map<String, PaymentPlaceAnalysisService.ApontamentoPraca> resultado =
                service.apontamentosPorDocumento(List.of(CEDENTE, "58.277.872/0009-39", OUTRO));

        assertThat(resultado.get(CEDENTE).titulos()).isEqualTo(2);
        assertThat(resultado.get(CEDENTE).valor()).isEqualByComparingTo("12048.05");
        assertThat(resultado.get(SACADO).titulos()).isEqualTo(2);
        assertThat(resultado.get(SACADO).valor()).isEqualByComparingTo("4025.40");
        assertThat(resultado).doesNotContainKey(OUTRO);
    }

    @Test
    @DisplayName("sem CNPJ válido não consulta o banco")
    void shouldSkipQueryWithoutValidDocuments() {
        assertThat(service.apontamentosPorDocumento(List.of("123", "52998224725"))).isEmpty();
        verify(entryRepository, never()).findCedenteDecidedForCompanies(any());
    }
}
