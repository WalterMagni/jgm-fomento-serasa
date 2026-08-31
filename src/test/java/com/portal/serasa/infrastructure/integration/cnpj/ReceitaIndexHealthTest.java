package com.portal.serasa.infrastructure.integration.cnpj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cobre o caminho em que o índice está ausente. O teste de integração só confirma o estado
 * bom; um alarme que nunca se viu disparar não é alarme.
 */
class ReceitaIndexHealthTest {

    private ReceitaIndexHealth healthWithIndexes(List<String> presentIndexes) {
        JdbcTemplate template = mock(JdbcTemplate.class);
        when(template.queryForList(anyString(), eq(String.class))).thenReturn(presentIndexes);

        ReceitaDataSourceProvider provider = mock(ReceitaDataSourceProvider.class);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.template(anyInt())).thenReturn(template);

        return new ReceitaIndexHealth(provider);
    }

    @Test
    @DisplayName("acusa o indice ausente e entrega o SQL de correcao")
    void shouldReportMissingIndexWithFix() {
        ReceitaIndexHealth health = healthWithIndexes(List.of("socios_pkey", "idx_socios_cnpj_basico"));

        assertThat(health.missingIndexes()).containsExactly("idx_socios_cpf_nome");
        assertThat(health.missingIndexesWithFix())
                .containsKey("idx_socios_cpf_nome");
        assertThat(health.missingIndexesWithFix().get("idx_socios_cpf_nome"))
                .contains("CREATE INDEX CONCURRENTLY")
                .contains("socios (cnpj_cpf_do_socio, nome_socio)");
    }

    @Test
    @DisplayName("nao acusa nada quando o indice existe")
    void shouldReportNothingWhenIndexIsPresent() {
        ReceitaIndexHealth health = healthWithIndexes(List.of("socios_pkey", "idx_socios_cpf_nome"));

        assertThat(health.missingIndexes()).isEmpty();
    }

    @Test
    @DisplayName("base indisponivel nao vira alarme de indice")
    void shouldStaySilentWhenReceitaIsUnavailable() {
        ReceitaDataSourceProvider provider = mock(ReceitaDataSourceProvider.class);
        when(provider.isAvailable()).thenReturn(false);

        // Sem conexão não dá para saber; avisar aqui seria alarme falso a cada boot com o banco fora.
        assertThat(new ReceitaIndexHealth(provider).missingIndexes()).isEmpty();
    }

    @Test
    @DisplayName("falha na consulta nao derruba o boot")
    void shouldSwallowQueryFailure() {
        JdbcTemplate template = mock(JdbcTemplate.class);
        when(template.queryForList(anyString(), eq(String.class)))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("sem conexao"));

        ReceitaDataSourceProvider provider = mock(ReceitaDataSourceProvider.class);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.template(anyInt())).thenReturn(template);

        ReceitaIndexHealth health = new ReceitaIndexHealth(provider);
        assertThat(health.missingIndexes()).isEmpty();
        health.warnOnStartup();
    }
}
