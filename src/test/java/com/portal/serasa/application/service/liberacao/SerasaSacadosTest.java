package com.portal.serasa.application.service.liberacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.pessoa.PessoaResumo;
import com.portal.serasa.domain.model.pessoa.TipoRestricao;
import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.repository.CreditAnalysisJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SerasaSacadosTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LocalDateTime CONSULTA = LocalDateTime.of(2026, 10, 14, 9, 30);

    @Mock private CreditAnalysisJpaRepository creditAnalysisRepository;
    @Mock private PersonAnalysisJpaRepository personAnalysisRepository;
    @InjectMocks private SerasaSacados serasaSacados;

    private static JsonNode json(String texto) throws Exception {
        return JSON.readTree(texto);
    }

    @Test
    @DisplayName("resumir: soma os sete tipos (ação judicial e falência pelos fatos), na ordem do TipoRestricao, e ignora tipo zerado")
    void shouldSumAllRestrictionTypes() throws Exception {
        JsonNode negativos = json("""
                {"pefin": {"summary": {"count": 2, "balance": 1000.5}},
                 "refin": {"summary": {"count": 0, "balance": 0}},
                 "notary": {"summary": {"count": 1, "balance": 345.17}},
                 "message": "CONSTA"}""");
        JsonNode fatos = json("""
                {"judgementFilings": {"summary": {"count": 1, "balance": 11000}},
                 "bankrupts": {"summary": {"count": 0}}}""");

        SerasaSacados.Resumo resumo = SerasaSacados.resumir(CONSULTA, negativos, fatos);

        assertThat(resumo.consultaEm()).isEqualTo(CONSULTA);
        assertThat(resumo.ocorrencias()).isEqualTo(4);
        assertThat(resumo.divida()).isEqualByComparingTo("12345.67");
        assertThat(resumo.restricoes()).extracting(PessoaResumo.Restricao::tipo)
                .containsExactly(TipoRestricao.PEFIN, TipoRestricao.PROTESTO, TipoRestricao.ACAO_JUDICIAL);
    }

    @Test
    @DisplayName("resumir: consulta sem restrição (ou sem as seções) é Nada consta: zero ocorrências e dívida zero")
    void shouldBeNothingFoundWithoutRestrictions() throws Exception {
        SerasaSacados.Resumo vazio = SerasaSacados.resumir(CONSULTA, json("{\"message\": \"NADA CONSTA\"}"), null);

        assertThat(vazio.ocorrencias()).isZero();
        assertThat(vazio.divida()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(vazio.restricoes()).isEmpty();
        assertThat(SerasaSacados.resumir(CONSULTA, null, null).ocorrencias()).isZero();
    }

    @Test
    @DisplayName("porDocumento: CNPJ pela consulta PJ, CPF pela PF; quem nunca foi consultado fica de fora")
    void shouldResolveCompaniesAndPeople() throws Exception {
        CreditAnalysisEntity empresa = new CreditAnalysisEntity();
        empresa.setCnpj("45723174000110");
        empresa.setConsultaEm(CONSULTA);
        empresa.setNegativeSummary(json("{\"pefin\": {\"summary\": {\"count\": 1, \"balance\": 50}}}"));
        PersonAnalysisEntity pessoa = new PersonAnalysisEntity();
        pessoa.setId(7L);
        pessoa.setCpf("12345678909");
        pessoa.setConsultaEm(CONSULTA.minusDays(45));
        pessoa.setFacts(json("{\"bankrupts\": {\"summary\": {\"count\": 1, \"balance\": 900}}}"));
        when(creditAnalysisRepository.findLatestByCnpjIn(List.of("45723174000110", "00360305000104"))).thenReturn(List.of(empresa));
        when(personAnalysisRepository.findLatestByCpfIn(List.of("12345678909"))).thenReturn(List.of(pessoa));

        Map<String, SerasaSacados.Resumo> resumos = serasaSacados.porDocumento(
                List.of("45723174000110", "12345678909", "00360305000104", "45723174000110"));

        assertThat(resumos).containsOnlyKeys("45723174000110", "12345678909");
        assertThat(resumos.get("45723174000110").divida()).isEqualByComparingTo("50");
        assertThat(resumos.get("12345678909").restricoes()).extracting(PessoaResumo.Restricao::tipo)
                .containsExactly(TipoRestricao.FALENCIA);
    }

    @Test
    @DisplayName("porDocumento: sem documento não vai ao banco")
    void shouldSkipQueriesWhenEmpty() {
        assertThat(serasaSacados.porDocumento(List.of())).isEmpty();
        verify(creditAnalysisRepository, never()).findLatestByCnpjIn(any());
        verify(personAnalysisRepository, never()).findLatestByCpfIn(any());
    }
}
