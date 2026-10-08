package com.portal.serasa.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAllowListTest {

    @Test
    @DisplayName("aceita e-mails separados por vírgula")
    void shouldParseCommaSeparatedEmails() {
        AdminAllowList lista = new AdminAllowList("walter@jgm.com,andressa@jgm.com");

        assertThat(lista.contem("walter@jgm.com")).isTrue();
        assertThat(lista.contem("andressa@jgm.com")).isTrue();
        assertThat(lista.contem("outra@jgm.com")).isFalse();
    }

    @Test
    @DisplayName("aceita e-mails separados por ponto e vírgula, e mistura dos dois")
    void shouldParseSemicolonSeparatedEmails() {
        AdminAllowList lista = new AdminAllowList("walter@jgm.com;andressa@jgm.com, carla@jgm.com ; bruna@jgm.com");

        assertThat(lista.contem("walter@jgm.com")).isTrue();
        assertThat(lista.contem("andressa@jgm.com")).isTrue();
        assertThat(lista.contem("carla@jgm.com")).isTrue();
        assertThat(lista.contem("bruna@jgm.com")).isTrue();
    }

    @Test
    @DisplayName("ignora maiúsculas/minúsculas tanto na configuração quanto na consulta")
    void shouldBeCaseInsensitive() {
        AdminAllowList lista = new AdminAllowList("Walter@JGM.com");

        assertThat(lista.contem("walter@jgm.com")).isTrue();
        assertThat(lista.contem("WALTER@JGM.COM")).isTrue();
        assertThat(lista.contem("Walter@Jgm.Com")).isTrue();
    }

    @Test
    @DisplayName("apara espaços ao redor, na configuração e na consulta")
    void shouldTrimWhitespace() {
        AdminAllowList lista = new AdminAllowList("  walter@jgm.com  ,\t andressa@jgm.com ");

        assertThat(lista.contem("  walter@jgm.com ")).isTrue();
        assertThat(lista.contem("andressa@jgm.com")).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("e-mail nulo ou em branco nunca é admin")
    void shouldRejectNullOrBlankEmail(String email) {
        AdminAllowList lista = new AdminAllowList("walter@jgm.com");

        assertThat(lista.contem(email)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", ",", ";", " , ; "})
    @DisplayName("configuração vazia ou só com separadores não torna ninguém admin (nem e-mail em branco)")
    void shouldGrantNobodyWhenConfigurationIsEmpty(String configurado) {
        AdminAllowList lista = new AdminAllowList(configurado);

        assertThat(lista.contem("walter@jgm.com")).isFalse();
        assertThat(lista.contem("")).isFalse();
        assertThat(lista.contem(" ")).isFalse();
        assertThat(lista.contem(null)).isFalse();
    }

    @Test
    @DisplayName("não confunde prefixo/sufixo: precisa ser o e-mail inteiro")
    void shouldRequireExactMatch() {
        AdminAllowList lista = new AdminAllowList("walter@jgm.com");

        assertThat(lista.contem("walter@jgm.com.br")).isFalse();
        assertThat(lista.contem("xwalter@jgm.com")).isFalse();
        assertThat(lista.contem("walter")).isFalse();
    }
}
