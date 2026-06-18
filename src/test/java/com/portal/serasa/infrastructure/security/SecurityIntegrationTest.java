package com.portal.serasa.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração do Spring Security.
 * Verifica que endpoints protegidos retornam 401 sem JWT,
 * e que os endpoints públicos estão acessíveis.
 * Pré-requisito: PostgreSQL rodando em localhost:5432 (docker-compose up).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "serasa.api.base-url=http://localhost:9999",
                "serasa.api.username=",
                "serasa.api.password="
        }
)
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // =========================================================
    // ENDPOINTS PROTEGIDOS: devem retornar 401 sem token JWT
    // =========================================================

    @Test
    @DisplayName("GET /api/v1/credit-analysis/1 retorna 401 sem token JWT")
    void creditAnalysisById_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/credit-analysis/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/credit-analysis/consultar/{cnpj} retorna 401 sem token JWT")
    void creditAnalysisConsultar_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/credit-analysis/consultar/12345678000195"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/clients retorna 401 sem token JWT")
    void clients_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/clients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/credit-analysis/1 retorna 401 com token Bearer malformado")
    void creditAnalysis_shouldReturn401_withMalformedToken() throws Exception {
        mockMvc.perform(get("/api/v1/credit-analysis/1")
                        .header("Authorization", "Bearer token.invalido.assinado"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/company/12345678000195 retorna 401 sem token JWT")
    void company_shouldReturn401_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/company/12345678000195"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================
    // ENDPOINTS PÚBLICOS: devem ser acessíveis sem token JWT
    // =========================================================

    @Test
    @DisplayName("POST /api/auth/login é público — retorna 401 de auth (não de segurança)")
    void loginEndpoint_shouldBePublic_notBlockedBySecurity() throws Exception {
        // 401 aqui = credenciais inválidas pelo controller, NÃO bloqueio do filtro JWT
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"naoexiste@test.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/register é público — cadastro de novo usuário retorna 201")
    void registerEndpoint_shouldBePublic_andReturn201() throws Exception {
        // UUID garante email único por execução de teste
        String uniqueEmail = "qa-" + UUID.randomUUID() + "@test.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"QA Test User","email":"%s","password":"senha123"}
                                """.formatted(uniqueEmail)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /v3/api-docs é público — Swagger/OpenAPI acessível sem token")
    void swaggerApiDocs_shouldBePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
