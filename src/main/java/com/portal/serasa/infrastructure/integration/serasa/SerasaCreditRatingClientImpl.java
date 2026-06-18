package com.portal.serasa.infrastructure.integration.serasa;

import com.portal.serasa.application.service.ApiRequestCounterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
@RequiredArgsConstructor
public class SerasaCreditRatingClientImpl implements SerasaCreditRatingClient {

    private static final String REPORT_PATH = "/credit-services/business-information-report/v1/reports";
    private static final String REPORT_NAME = "RELATORIO_AVANCADO_PJ_ANALITICO";
    private static final String OPTIONAL_FEATURES = "QSA_AVANCADO";
    private static final String REPORT_PARAMETERS = "ew0KICAgICJyZXBvcnRQYXJhbWV0ZXJzIjogWw0KDQogICAgICAgIHsNCgkJCSJuYW1lIiA6ICJzZWdtZW50Q29kZSIsDQoJCQkidmFsdWUiIDogIjAyOCINCiAgICAgICAgfQkJDQogICAgDQogICAgXQ0KfQ";

    @Value("${serasa.api.base-url}")
    private String baseUrl;

    private final SerasaAuthService authService;
    private final RestClient.Builder restClientBuilder;
    private final ApiRequestCounterService apiRequestCounterService;

    @Override
    public String consultarCreditRating(String cnpj) {
        String documentNumber = cnpj.replaceAll("\\D", "");
        if (documentNumber.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter 14 dígitos");
        }

        log.debug("Consultando Credit Rating Serasa para CNPJ: {}", documentNumber);

        String token = authService.getAccessToken();

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-document-id", documentNumber)
                .build();

        String rawJson = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(REPORT_PATH)
                        .queryParam("reportName", REPORT_NAME)
                        .queryParam("optionalFeatures", OPTIONAL_FEATURES)
                        .queryParam("reportParameters", REPORT_PARAMETERS)
                        .build())
                .retrieve()
                .body(String.class);

        apiRequestCounterService.increment(ApiRequestCounterService.PROVIDER_SERASA);
        return rawJson;
    }
}
