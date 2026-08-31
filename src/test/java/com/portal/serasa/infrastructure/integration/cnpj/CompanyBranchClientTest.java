package com.portal.serasa.infrastructure.integration.cnpj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyBranchClientTest {

    /** Provider apontado para uma porta morta: nunca fica disponivel. */
    private static ReceitaDataSourceProvider unreachableProvider() {
        ReceitaDataSourceProvider provider = new ReceitaDataSourceProvider();
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "url", "jdbc:postgresql://127.0.0.1:1/cnpj");
        ReflectionTestUtils.setField(provider, "username", "postgres");
        ReflectionTestUtils.setField(provider, "password", "postgres");
        ReflectionTestUtils.setField(provider, "poolSize", 2);
        return provider;
    }

    @Test
    @DisplayName("fetchBranches: responde indisponivel quando a base da Receita esta fora")
    void shouldReportUnavailableWhenReceitaIsDown() {
        CompanyBranchClient client = new CompanyBranchClient(unreachableProvider());
        ReflectionTestUtils.setField(client, "maxRows", 10);

        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.fetchBranches("12345678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponivel");
    }
}
