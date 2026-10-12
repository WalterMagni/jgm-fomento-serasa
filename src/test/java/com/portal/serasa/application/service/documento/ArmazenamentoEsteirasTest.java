package com.portal.serasa.application.service.documento;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArmazenamentoEsteirasTest {

    @TempDir
    Path temp;

    @Test
    @DisplayName("cria a pasta na primeira vez e devolve o caminho real")
    void shouldCreateFolderOnFirstUse() throws IOException {
        Path pasta = temp.resolve("dados/esteiras");

        Path base = new ArmazenamentoEsteiras(pasta.toString()).base();

        assertThat(Files.isDirectory(pasta)).isTrue();
        assertThat(base).isEqualTo(pasta.toRealPath());
        // segunda chamada com a pasta já existente
        assertThat(new ArmazenamentoEsteiras(pasta.toString()).base()).isEqualTo(base);
    }

    @Test
    @DisplayName("caminho que não dá para criar vira erro de uso com a pasta na mensagem")
    void shouldFailWithFolderInMessage() throws IOException {
        Path arquivo = Files.createFile(temp.resolve("sou-um-arquivo"));
        String pasta = arquivo.resolve("esteiras").toString();

        assertThatThrownBy(() -> new ArmazenamentoEsteiras(pasta).base())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pasta de arquivos das esteiras indisponível")
                .hasMessageContaining("esteiras");
    }
}
