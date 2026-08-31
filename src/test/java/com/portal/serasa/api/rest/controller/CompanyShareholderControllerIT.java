package com.portal.serasa.api.rest.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercita os endpoints de quadro societário contra as bases reais. Depende do Postgres da
 * aplicação e da cópia local da Receita, então fica fora da suíte padrão — rode com
 * {@code mvn test -Dtest=CompanyShareholderControllerIT -Dreceita.it=true}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "receita.it", matches = "true")
class CompanyShareholderControllerIT {

    /** LIPSON COSMETICOS — sócios em comum com uma empresa baixada (REDDEN). */
    private static final String LIPSON = "61610515";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /socios: lista socios com a contagem de outras empresas")
    @WithMockUser
    void shouldListShareholders() throws Exception {
        mockMvc.perform(get("/api/v1/company/{cnpj}/socios", LIPSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].documentMask").isNotEmpty())
                .andExpect(jsonPath("$[0].otherCompaniesCount").isNumber());
    }

    @Test
    @DisplayName("GET /grupo-economico: traz empresas ligadas por socio em comum, irregulares primeiro")
    @WithMockUser
    void shouldListEconomicGroup() throws Exception {
        mockMvc.perform(get("/api/v1/company/{cnpj}/grupo-economico", LIPSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].cnpjRaiz").isNotEmpty())
                // REDDEN ARMAZENAGEM esta baixada (situacao 08) e compartilha os tres socios,
                // entao deve encabecar a lista pela ordenacao por irregularidade.
                .andExpect(jsonPath("$[0].irregular").value(true))
                .andExpect(jsonPath("$[0].companyStatusLabel").value("Baixada"))
                .andExpect(jsonPath("$[0].sharedShareholders").isArray())
                .andExpect(jsonPath("$[0].sharedShareholders[0].name").isNotEmpty());
    }

    /**
     * O painel do frontend le estes campos por nome. Renomear qualquer um quebra a tela sem
     * quebrar compilacao de nenhum dos lados, entao o contrato fica fixado aqui.
     */
    @Test
    @DisplayName("contrato: os campos consumidos pelo painel existem na resposta")
    @WithMockUser
    void shouldExposeFieldsConsumedByThePanel() throws Exception {
        mockMvc.perform(get("/api/v1/company/{cnpj}/socios", LIPSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].document").exists())
                .andExpect(jsonPath("$[0].documentMask").exists())
                .andExpect(jsonPath("$[0].documentType").exists())
                .andExpect(jsonPath("$[0].documentSource").exists())
                .andExpect(jsonPath("$[0].qualification").exists())
                .andExpect(jsonPath("$[0].entryDate").exists())
                .andExpect(jsonPath("$[0].otherCompaniesCount").exists());

        mockMvc.perform(get("/api/v1/company/{cnpj}/grupo-economico", LIPSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cnpjRaiz").exists())
                .andExpect(jsonPath("$[0].companyName").exists())
                .andExpect(jsonPath("$[0].companyStatusLabel").exists())
                .andExpect(jsonPath("$[0].irregular").exists())
                .andExpect(jsonPath("$[0].inSystem").exists())
                .andExpect(jsonPath("$[0].alreadyLinked").exists())
                .andExpect(jsonPath("$[0].sharedShareholders[0].name").exists())
                // 'cnpj' e o CNPJ completo usado para vincular como parceira; nulo fora do portal,
                // mas a chave precisa existir para o botao decidir o que mostrar.
                .andExpect(jsonPath("$[0]", org.hamcrest.Matchers.hasKey("cnpj")));
    }

    @Test
    @DisplayName("GET /socios: rejeita CNPJ com formato invalido")
    @WithMockUser
    void shouldRejectMalformedCnpj() throws Exception {
        mockMvc.perform(get("/api/v1/company/{cnpj}/socios", "abc"))
                .andExpect(status().is4xxClientError());
    }
}
