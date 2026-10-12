package com.portal.serasa.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.application.port.out.UltimaAnalisePessoa;
import com.portal.serasa.domain.model.pessoa.PessoaResumo;
import com.portal.serasa.domain.model.pessoa.SituacaoPessoa;
import com.portal.serasa.domain.model.pessoa.TipoRestricao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PessoaResumoMontadorTest {

    private final PessoaResumoMontador montador = new PessoaResumoMontador(new ObjectMapper());

    private static final String SEM_RESTRICAO = """
            {"pefin":{"count":0,"balance":0.0},"refin":{"count":0,"balance":0.0},
             "check":{"count":0,"balance":0.0},"notary":{"count":0,"balance":0.0},
             "collectionRecords":{"count":0,"balance":0.0},
             "judgementFilings":{"count":0,"balance":0.0},"bankrupts":{"count":0,"balance":0.0}}
            """;

    private static UltimaAnalisePessoa analise(String restricoesJson, String sociosJson) {
        return new UltimaAnalisePessoa("04321650887", "MARIA DA SILVA", LocalDateTime.of(2026, 10, 1, 9, 30),
                "AMERICANA", "SP", restricoesJson, sociosJson);
    }

    @Test
    @DisplayName("soma quantidade e valor por tipo, incluindo ações e falências dos fatos")
    void shouldSumEveryRestrictionType() {
        String json = """
                {"pefin":{"count":2,"balance":1500.50},"refin":{"count":0,"balance":0.0},
                 "check":{"count":0,"balance":0.0},"notary":{"count":1,"balance":800},
                 "collectionRecords":null,
                 "judgementFilings":{"count":1,"balance":2000.0},"bankrupts":{"count":0,"balance":0.0}}
                """;

        PessoaResumo resumo = montador.montar(analise(json, null), Map.of());

        assertThat(resumo.restricoes())
                .extracting(PessoaResumo.Restricao::tipo, PessoaResumo.Restricao::quantidade)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TipoRestricao.PEFIN, 2),
                        org.assertj.core.groups.Tuple.tuple(TipoRestricao.PROTESTO, 1),
                        org.assertj.core.groups.Tuple.tuple(TipoRestricao.ACAO_JUDICIAL, 1));
        assertThat(resumo.totalOcorrencias()).isEqualTo(4);
        assertThat(resumo.dividaTotal()).isEqualByComparingTo("4300.50");
        assertThat(resumo.situacao()).isEqualTo(SituacaoPessoa.CRITICO);
    }

    @Test
    @DisplayName("sem nenhuma ocorrência a pessoa é limpa e não lista restrições")
    void shouldBeCleanWithoutOccurrences() {
        PessoaResumo resumo = montador.montar(analise(SEM_RESTRICAO, null), Map.of());

        assertThat(resumo.restricoes()).isEmpty();
        assertThat(resumo.totalOcorrencias()).isZero();
        assertThat(resumo.dividaTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resumo.situacao()).isEqualTo(SituacaoPessoa.LIMPO);
    }

    @Test
    @DisplayName("vínculo só com empresa da carteira, CNPJ normalizado e sem repetir")
    void shouldLinkOnlyPortfolioCompanies() {
        String socios = """
                [{"businessDocument":"12.345.678/0001-90","companyName":"NA CARTEIRA"},
                 {"businessDocument":"12345678000190"},
                 {"businessDocument":"98765432000110","companyName":"FORA DA CARTEIRA"},
                 {"businessDocument":"123"}]
                """;

        PessoaResumo resumo = montador.montar(analise(SEM_RESTRICAO, socios),
                Map.of("12345678000190", "TECELAGEM PRIMAVERA LTDA"));

        assertThat(resumo.vinculos())
                .containsExactly(new PessoaResumo.Vinculo("12345678000190", "TECELAGEM PRIMAVERA LTDA"));
    }

    @Test
    @DisplayName("JSON ausente ou quebrado não derruba a linha: pessoa sai sem restrições")
    void shouldTolerateMissingOrBrokenJson() {
        assertThat(montador.montar(analise(null, null), Map.of()).situacao()).isEqualTo(SituacaoPessoa.LIMPO);
        assertThat(montador.montar(analise("{quebrado", "[x"), Map.of()).vinculos()).isEmpty();
    }

    @ParameterizedTest(name = "{0} ocorrência(s) e dívida {1} → {2}")
    @CsvSource({
            "0, 0, LIMPO",
            "1, 1000, COM_RESTRICAO",
            "2, 50000, COM_RESTRICAO",
            "2, 50000.01, CRITICO",
            "1, 80000, CRITICO",
            "3, 100, CRITICO"
    })
    @DisplayName("situação: crítico a partir de 3 ocorrências ou dívida acima de R$ 50 mil")
    void shouldClassifySituation(int ocorrencias, String divida, SituacaoPessoa esperada) {
        assertThat(SituacaoPessoa.classificar(ocorrencias, new BigDecimal(divida))).isEqualTo(esperada);
    }
}
