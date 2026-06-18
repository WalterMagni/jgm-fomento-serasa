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
public class SerasaPersonCreditRatingClientImpl implements SerasaPersonCreditRatingClient {

    private static final String REPORT_PATH  = "/credit-services/person-information-report/v1/creditreport";
    private static final String REPORT_NAME  = "RELATORIO_AVANCADO_PF";

    @Value("${serasa.api.base-url}")
    private String baseUrl;

    private final SerasaAuthService authService;
    private final RestClient.Builder restClientBuilder;
    private final ApiRequestCounterService apiRequestCounterService;

    @Override
    public String consultarCreditRatingPF(String cpf) {
        String documentNumber = cpf.replaceAll("\\D", "");
        if (documentNumber.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter 11 dígitos");
        }

        log.debug("Consultando Credit Rating Serasa PF para CPF: {}", documentNumber);

        String token = authService.getAccessToken();

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-document-id", documentNumber)
                .build();

        String rawJson = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(REPORT_PATH)
                        .queryParam("reportName", REPORT_NAME)
                        .build())
                .retrieve()
                .body(String.class);

        apiRequestCounterService.increment(ApiRequestCounterService.PROVIDER_SERASA);
        return rawJson;
    }
}
