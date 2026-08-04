package com.portal.serasa.infrastructure.integration.cnpj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyBranchClientTest {

    @Test
    @DisplayName("init: nao deve derrubar o boot quando o Postgres auxiliar estiver indisponivel")
    void shouldNotFailBootWhenAuxiliaryPostgresIsUnavailable() {
        CompanyBranchClient client = new CompanyBranchClient();
        ReflectionTestUtils.setField(client, "enabled", true);
        ReflectionTestUtils.setField(client, "url", "jdbc:postgresql://127.0.0.1:1/cnpj");
        ReflectionTestUtils.setField(client, "username", "postgres");
        ReflectionTestUtils.setField(client, "password", "postgres");
        ReflectionTestUtils.setField(client, "maxRows", 10);

        assertThatCode(client::init).doesNotThrowAnyException();
        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.fetchBranches("12345678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponivel");
    }
}
