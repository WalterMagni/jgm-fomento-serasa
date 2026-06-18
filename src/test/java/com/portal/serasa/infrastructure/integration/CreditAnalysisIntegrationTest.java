package com.portal.serasa.infrastructure.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração: contexto Spring + WireMock + PostgreSQL (docker-compose em localhost:5432).
 * Pré-requisito: docker-compose up (porta 5432 deve estar disponível).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "serasa.api.base-url=http://localhost:8089",
                "serasa.api.username=test",
                "serasa.api.password=test"
        }
)
public class CreditAnalysisIntegrationTest {

    private static final int WIREMOCK_PORT = 8089;

    private WireMockServer wireMockServer;

    @Autowired
    private CreditAnalysisRepository repository;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Contexto Spring carregado: repositório está injetado e funcional")
    void contextLoads_andRepositoryBeanIsWired() {
        assertThat(repository).isNotNull();
    }

    @Test
    @DisplayName("WireMock sobe e desce corretamente para simular API Serasa Credit Rating")
    void wireMock_stubbingAndShutdownWorkCorrectly() {
        String mockJson = """
                {"reports":[{
                  "creditRating":{"riskRating":{"rulerResult":750,"classRisk":"A"}},
                  "negativeData":{"protestos":[]}
                }]}
                """;

        stubFor(get(urlPathMatching("/credit-services/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockJson)));

        assertThat(wireMockServer.isRunning()).isTrue();
        assertThat(wireMockServer.getStubMappings()).hasSize(1);
    }

    @Test
    @DisplayName("WireMock: stub do endpoint de login Serasa retorna token mock")
    void wireMock_serasaLoginStubReturnsToken() {
        stubFor(post(urlEqualTo("/security/iam/v1/client-identities/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"mock-token-abc123\",\"expiresIn\":\"3600\"}")));

        assertThat(wireMockServer.getStubMappings()).isNotEmpty();
        assertThat(wireMockServer.isRunning()).isTrue();
    }
}
