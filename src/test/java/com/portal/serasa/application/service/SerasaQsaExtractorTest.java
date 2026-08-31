package com.portal.serasa.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SerasaQsaExtractorTest {

    private final SerasaQsaExtractor extractor = new SerasaQsaExtractor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Recorte fiel de credit_analysis.partner_details, incluindo a repeticao socio/diretor. */
    private static final String QSA_JSON = """
            {
              "partnerCompleteReport": {
                "partnersList": [
                  {"name": "ALESSIO DE TOLEDO RODRIGUES", "documentId": "04321650887",
                   "documentType": "CPF", "status": "ATIVA", "sinceDate": "1989-09-15",
                   "capitalTotalValue": 47.5},
                  {"name": "REGINA HELENA REHDER DE TOLEDO RODRIGUES", "documentId": "87349264887",
                   "documentType": "CPF", "status": "ATIVA", "sinceDate": "1989-09-15",
                   "capitalTotalValue": 47.5},
                  {"name": "MACAB PARTICIPACOES SOCIETARIAS LTDA", "documentId": "34438918000139",
                   "documentType": "CNPJ", "status": "ATIVA"}
                ]
              },
              "directorCompleteReport": {
                "directorsList": [
                  {"name": "ALESSIO DE TOLEDO RODRIGUES", "documentId": "04321650887",
                   "role": "ADMINISTRADOR", "status": "ATIVA", "sinceDate": "1989-09-15"}
                ]
              }
            }
            """;

    private List<SerasaQsaExtractor.QsaPartner> extract(String json) throws Exception {
        return extractor.extract(objectMapper.readTree(json));
    }

    @Test
    @DisplayName("extrai CPF completo, capital e cargo, deduplicando socio que tambem e diretor")
    void shouldExtractAndMergePartnersAndDirectors() throws Exception {
        List<SerasaQsaExtractor.QsaPartner> partners = extract(QSA_JSON);

        // Socio PJ fica de fora: a identidade dele na Receita e o CNPJ, tratado pela outra carga.
        assertThat(partners).hasSize(2);

        SerasaQsaExtractor.QsaPartner alessio = partners.stream()
                .filter(p -> p.cpf().equals("04321650887")).findFirst().orElseThrow();
        assertThat(alessio.name()).isEqualTo("ALESSIO DE TOLEDO RODRIGUES");
        assertThat(alessio.capitalPercent()).isEqualByComparingTo(new BigDecimal("47.5"));
        assertThat(alessio.sinceDate()).isEqualTo(LocalDate.of(1989, 9, 15));
        // O cargo so existe na lista de diretores — prova que as duas listas foram fundidas.
        assertThat(alessio.role()).isEqualTo("ADMINISTRADOR");
        assertThat(alessio.status()).isEqualTo("ATIVA");
    }

    @Test
    @DisplayName("o CPF extraido gera a mascara que casa com a base da Receita")
    void shouldProduceCpfThatMatchesReceitaMask() throws Exception {
        List<SerasaQsaExtractor.QsaPartner> partners = extract(QSA_JSON);

        assertThat(partners).extracting(p -> CpfMask.fromCpf(p.cpf()))
                .containsExactlyInAnyOrder("***216508**", "***492648**");
    }

    @Test
    @DisplayName("tolera QSA ausente, vazio ou com campos faltando")
    void shouldTolerateMissingData() throws Exception {
        assertThat(extractor.extract(null)).isEmpty();
        assertThat(extract("{}")).isEmpty();
        assertThat(extract("""
                {"partnerCompleteReport": {"partnersList": [
                  {"name": "SEM DOCUMENTO"},
                  {"documentId": "04321650887"},
                  {"name": "OK", "documentId": "04321650887", "sinceDate": "data-invalida"}
                ]}}
                """)).singleElement()
                .satisfies(p -> {
                    assertThat(p.name()).isEqualTo("OK");
                    assertThat(p.sinceDate()).isNull();
                });
    }
}
