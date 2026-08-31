package com.portal.serasa.infrastructure.integration.cnpj;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceitaDataSourceProviderTest {

    private static ReceitaDataSourceProvider unreachableProvider() {
        ReceitaDataSourceProvider provider = new ReceitaDataSourceProvider();
        ReflectionTestUtils.setField(provider, "enabled", true);
        ReflectionTestUtils.setField(provider, "url", "jdbc:postgresql://127.0.0.1:1/cnpj");
        ReflectionTestUtils.setField(provider, "username", "postgres");
        ReflectionTestUtils.setField(provider, "password", "postgres");
        ReflectionTestUtils.setField(provider, "poolSize", 2);
        ReflectionTestUtils.setField(provider, "retryCooldownSeconds", 300);
        return provider;
    }

    @Test
    @DisplayName("init: nao deve derrubar o boot quando o Postgres da Receita estiver indisponivel")
    void shouldNotFailBootWhenReceitaPostgresIsUnavailable() {
        ReceitaDataSourceProvider provider = unreachableProvider();

        assertThatCode(provider::init).doesNotThrowAnyException();
        assertThat(provider.isAvailable()).isFalse();
        assertThatThrownBy(() -> provider.template(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponivel");
    }

    @Test
    @DisplayName("cooldown: nao repete a tentativa de conexao dentro da janela")
    void shouldNotRetryWithinCooldownWindow() {
        ReceitaDataSourceProvider provider = unreachableProvider();

        provider.isAvailable(); // primeira tentativa: paga o connectionTimeout inteiro

        long startedAt = System.nanoTime();
        assertThat(provider.isAvailable()).isFalse();
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        // Sem cooldown esta chamada custaria outro connectionTimeout (3s).
        assertThat(elapsedMillis).isLessThan(500);
    }

    @Test
    @DisplayName("desabilitado: nao tenta conectar e reporta indisponivel")
    void shouldStayDisabledWhenFlagIsOff() {
        ReceitaDataSourceProvider provider = new ReceitaDataSourceProvider();
        ReflectionTestUtils.setField(provider, "enabled", false);
        ReflectionTestUtils.setField(provider, "url", "");

        assertThatCode(provider::init).doesNotThrowAnyException();
        assertThat(provider.isAvailable()).isFalse();
    }
}
