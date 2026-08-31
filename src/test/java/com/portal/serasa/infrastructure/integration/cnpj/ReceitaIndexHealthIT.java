package com.portal.serasa.infrastructure.integration.cnpj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alarme de regressão para o índice da busca reversa de sócios. Se o cnpj-data-pipeline
 * recarregar as tabelas e levar o índice junto, nada quebra — as consultas só ficam ~100x mais
 * lentas, em silêncio. Este teste transforma esse silêncio em falha visível.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "receita.it", matches = "true")
class ReceitaIndexHealthIT {

    @Autowired
    private ReceitaIndexHealth indexHealth;

    @Test
    @DisplayName("a base da Receita tem todos os indices exigidos pelo portal")
    void shouldHaveEveryRequiredIndex() {
        assertThat(indexHealth.missingIndexesWithFix())
                .as("indices ausentes — rode scripts/receita-indices.sql")
                .isEmpty();
    }
}
