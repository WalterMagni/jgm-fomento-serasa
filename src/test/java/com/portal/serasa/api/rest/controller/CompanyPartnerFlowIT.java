package com.portal.serasa.api.rest.controller;

import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo de vínculo manual de empresa parceira ponta a ponta: buscar candidata e vincular.
 *
 * <p>Autentica com o principal real ({@link UserEntity}), e não com {@code @WithMockUser}, porque
 * o controller só aceita esse tipo — com um principal de String ele responde 401 e o teste
 * passaria a medir a coisa errada.</p>
 *
 * <p>{@code @Transactional} faz rollback ao final: o vínculo criado não fica no banco.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
@EnabledIfSystemProperty(named = "receita.it", matches = "true")
class CompanyPartnerFlowIT {

    private static final String COMPANY_A = "00877761000126"; // DEL MORO & DEL MORO LTDA
    private static final String COMPANY_B = "00960089000138"; // DINIZ GREGORIO

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void authenticate() {
        UserEntity user = userRepository.findAll().stream().findFirst().orElseThrow();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @Test
    @DisplayName("busca de candidatas encontra empresa cadastrada por trecho do nome")
    void shouldFindCandidateByNameFragment() throws Exception {
        mockMvc.perform(get("/api/v1/company/{cnpj}/parceiras/busca", COMPANY_A).param("q", "diniz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cnpj == '" + COMPANY_B + "')]").exists());
    }

    @Test
    @DisplayName("vincular empresa parceira cria o vinculo e ele aparece na listagem")
    void shouldLinkPartnerCompany() throws Exception {
        mockMvc.perform(post("/api/v1/company/{cnpj}/parceiras", COMPANY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partnerCnpj\":\"" + COMPANY_B + "\"}"))
                .andExpect(status().isOk());

        // O vínculo é bidirecional: precisa aparecer dos dois lados.
        mockMvc.perform(get("/api/v1/company/{cnpj}/parceiras", COMPANY_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cnpj == '" + COMPANY_B + "')]").exists());

        mockMvc.perform(get("/api/v1/company/{cnpj}/parceiras", COMPANY_B))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cnpj == '" + COMPANY_A + "')]").exists());
    }
}
