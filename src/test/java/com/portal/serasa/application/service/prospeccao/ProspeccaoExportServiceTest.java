package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProspeccaoExportServiceTest {

    @Test
    @DisplayName("escapar: campo simples sai sem aspas")
    void shouldLeavePlainValueAlone() {
        assertThat(ProspeccaoExportService.escapar("Receita Federal")).isEqualTo("Receita Federal");
        assertThat(ProspeccaoExportService.escapar(null)).isEmpty();
        assertThat(ProspeccaoExportService.escapar("")).isEmpty();
    }

    @Test
    @DisplayName("escapar: ponto e vírgula no texto não pode virar coluna nova")
    void shouldQuoteSeparator() {
        // O separador é ';' porque o Excel em português usa a vírgula como decimal. Observação de
        // documento é texto livre e chega com ';' de vez em quando.
        assertThat(ProspeccaoExportService.escapar("Pendente; cobrar de novo"))
                .isEqualTo("\"Pendente; cobrar de novo\"");
    }

    @Test
    @DisplayName("escapar: aspas dentro do texto são dobradas")
    void shouldDoubleQuotes() {
        assertThat(ProspeccaoExportService.escapar("Cliente disse \"semana que vem\""))
                .isEqualTo("\"Cliente disse \"\"semana que vem\"\"\"");
    }

    @Test
    @DisplayName("escapar: quebra de linha fica dentro do campo, não parte a linha do CSV")
    void shouldQuoteNewline() {
        assertThat(ProspeccaoExportService.escapar("linha 1\nlinha 2"))
                .isEqualTo("\"linha 1\nlinha 2\"");
        assertThat(ProspeccaoExportService.escapar("linha 1\r\nlinha 2"))
                .isEqualTo("\"linha 1\r\nlinha 2\"");
    }

    @Test
    @DisplayName("escapar: caso real da planilha de origem, com vírgulas e hífen")
    void shouldHandleRealSpreadsheetNote() {
        // Vírgula sozinha não exige aspas com este separador — o Excel abre certo mesmo assim.
        assertThat(ProspeccaoExportService.escapar("OK - Sofisa, Bradesco, Itaú"))
                .isEqualTo("OK - Sofisa, Bradesco, Itaú");
    }

    @ParameterizedTest
    @CsvSource({
            "11222333000181, 11.222.333/0001-81",
            "00000000000191, 00.000.000/0001-91"
    })
    @DisplayName("CNPJ sai formatado, que é como o time lê na planilha")
    void shouldFormatCnpj(String cru, String esperado) {
        assertThat(ProspeccaoExportService.cnpjFormatado(cru)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("CNPJ fora do padrão sai como está, sem estourar")
    void shouldNotBreakOnOddCnpj() {
        assertThat(ProspeccaoExportService.cnpjFormatado("123")).isEqualTo("123");
        assertThat(ProspeccaoExportService.cnpjFormatado(null)).isEmpty();
    }

    @Test
    @DisplayName("estágio sai com o mesmo rótulo que a tela mostra")
    void shouldUseScreenLabels() {
        assertThat(ProspeccaoExportService.rotulo(EstagioProspeccao.DOCS_PENDENTES)).isEqualTo("Documentos pendentes");
        // "Inerte" é a palavra que o time usa para o cliente que parou de responder.
        assertThat(ProspeccaoExportService.rotulo(EstagioProspeccao.REMOVIDO_RADAR)).isEqualTo("Inerte");
    }

    @Test
    @DisplayName("todo estágio tem rótulo — enum novo não pode sair como nome técnico")
    void shouldLabelEveryStage() {
        for (EstagioProspeccao estagio : EstagioProspeccao.values()) {
            assertThat(ProspeccaoExportService.rotulo(estagio))
                    .as("rótulo de %s", estagio)
                    .isNotBlank()
                    .doesNotContain("_");
        }
    }

    @Test
    @DisplayName("o arquivo começa com a marca de bytes, senão o Excel come os acentos")
    void shouldStartWithBom() {
        assertThat(ProspeccaoExportService.BOM).isEqualTo("﻿");
    }
}
