package com.portal.serasa.infrastructure.integration.bacen;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

/**
 * Consulta o cadastro público de agências do Banco Central (API OLINDA).
 * Chaveado por CnpjBase (8 dígitos do CNPJ do banco) + CodigoCompe (número da agência).
 */
@Slf4j
@Component
public class BacenAgencyClient {

    private final RestClient bacenRestClient;

    public BacenAgencyClient(@Qualifier("bacenRestClient") RestClient bacenRestClient) {
        this.bacenRestClient = bacenRestClient;
    }

    public Optional<BacenAgency> fetchAgency(String cnpjBase, String codigoCompe) {
        if (cnpjBase == null || codigoCompe == null) {
            return Optional.empty();
        }
        String filter = String.format("CnpjBase eq '%s' and CodigoCompe eq '%s'", cnpjBase, codigoCompe);
        try {
            OlindaResponse response = bacenRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/Agencias")
                            .queryParam("$filter", filter)
                            .queryParam("$top", "1")
                            .queryParam("$format", "json")
                            .build())
                    .retrieve()
                    .body(OlindaResponse.class);
            if (response == null || response.value() == null || response.value().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(response.value().get(0));
        } catch (Exception e) {
            log.warn("Falha ao consultar agência Bacen (cnpjBase={}, compe={}): {}", cnpjBase, codigoCompe, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OlindaResponse(List<BacenAgency> value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BacenAgency(
            @JsonProperty("NomeIf") String nomeIf,
            @JsonProperty("Segmento") String segmento,
            @JsonProperty("CodigoCompe") String codigoCompe,
            @JsonProperty("NomeAgencia") String nomeAgencia,
            @JsonProperty("Endereco") String endereco,
            @JsonProperty("Numero") String numero,
            @JsonProperty("Complemento") String complemento,
            @JsonProperty("Bairro") String bairro,
            @JsonProperty("Cep") String cep,
            @JsonProperty("MunicipioIbge") String municipioIbge,
            @JsonProperty("Municipio") String municipio,
            @JsonProperty("UF") String uf,
            @JsonProperty("DDD") String ddd,
            @JsonProperty("Telefone") String telefone
    ) {
    }
}
