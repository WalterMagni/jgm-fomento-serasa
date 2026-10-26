package com.portal.serasa.application.service.judit;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Planilha dos processos: só os que a tela mostra, na ordem dela, e o resumo que explica os números. */
@ExtendWith(MockitoExtension.class)
class JuditExportServiceTest {

    private static final String CNPJ = "12021039000174";

    @Mock private JuditService juditService;

    private JuditExportService service;

    @BeforeEach
    void setUp() {
        service = new JuditExportService(juditService);
    }

    private static JuditLeitura.Processo processo(String cnj, String polo, BigDecimal valor) {
        return new JuditLeitura.Processo(cnj, List.of(1, 2), "BANCO X EMPRESA", polo, List.of("BANCO DO BRASIL S/A"),
                "TJPR", List.of("3ª VARA CÍVEL"), "PR", "CURITIBA", "DIREITO CIVIL", "EXECUÇÃO DE TÍTULO EXTRAJUDICIAL",
                List.of("DUPLICATA", "CHEQUE"), valor, LocalDate.of(2026, 3, 2),
                new JuditLeitura.Andamento(LocalDate.of(2026, 8, 11), "CITAÇÃO"), false, LocalDate.of(2026, 8, 23),
                "ESTADUAL", "EM_TRAMITACAO", true);
    }

    private void listaGuardada() {
        JuditLeitura.Processos dados = new JuditLeitura.Processos(3, 2, 1, 1, new BigDecimal("1000.50"), 0, List.of(),
                List.of(processo("0001", JuditLeitura.REU, new BigDecimal("1000.50")),
                        processo("0002", JuditLeitura.AUTOR, null)));
        when(juditService.processos(CNPJ)).thenReturn(new JuditService.ProcessosConsultados(
                LocalDateTime.of(2026, 10, 15, 20, 50), "Walter", new BigDecimal("1.50"), false, false, 54, dados, null,
                java.util.Map.of()));
    }

    private static XSSFWorkbook ler(JuditExportService.Planilha planilha) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(planilha.conteudo()));
    }

    @Test
    @DisplayName("exporta só os CNJs pedidos, com polo em português e valor como número")
    void exportaFiltrados() throws Exception {
        listaGuardada();

        JuditExportService.Planilha planilha = service.exportar(CNPJ, List.of("0001"));

        assertThat(planilha.nome()).startsWith("processos-judit-12021039000174-").endsWith(".xlsx");
        try (XSSFWorkbook arquivo = ler(planilha)) {
            Sheet processos = arquivo.getSheet("Processos");
            assertThat(processos.getLastRowNum()).as("cabeçalho + 1 processo").isEqualTo(1);
            Row linha = processos.getRow(1);
            assertThat(linha.getCell(0).getStringCellValue()).isEqualTo("0001");
            assertThat(linha.getCell(1).getStringCellValue()).isEqualTo("1ª, 2ª");
            assertThat(linha.getCell(2).getStringCellValue()).isEqualTo("Ré");
            assertThat(linha.getCell(6).getStringCellValue()).isEqualTo("DUPLICATA; CHEQUE");
            assertThat(linha.getCell(12).getNumericCellValue()).isEqualTo(1000.50);
            assertThat(linha.getCell(13).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(LocalDate.of(2026, 3, 2));

            Sheet resumo = arquivo.getSheet("Resumo");
            assertThat(resumo.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Processos na lista");
            assertThat(resumo.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(resumo.getRow(5).getCell(1).getNumericCellValue()).as("nesta planilha").isEqualTo(1);
        }
    }

    @Test
    @DisplayName("respeita a ordem da tela e ignora número desconhecido ou repetido")
    void ordemDaTela() throws Exception {
        listaGuardada();

        try (XSSFWorkbook arquivo = ler(service.exportar(CNPJ, List.of("0002", "9999", "0001", "0002")))) {
            Sheet processos = arquivo.getSheet("Processos");
            assertThat(processos.getLastRowNum()).isEqualTo(2);
            assertThat(processos.getRow(1).getCell(0).getStringCellValue()).isEqualTo("0002");
            assertThat(processos.getRow(2).getCell(0).getStringCellValue()).isEqualTo("0001");
        }
    }

    @Test
    @DisplayName("sem lista de CNJs exporta todos; processo sem valor fica com a célula vazia")
    void exportaTodos() throws Exception {
        listaGuardada();

        try (XSSFWorkbook arquivo = ler(service.exportar(CNPJ, null))) {
            Sheet processos = arquivo.getSheet("Processos");
            assertThat(processos.getLastRowNum()).isEqualTo(2);
            assertThat(processos.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Autora");
            assertThat(processos.getRow(2).getCell(12).toString()).isEmpty();
        }
    }

    @Test
    @DisplayName("sem lista consultada não há o que exportar")
    void semLista() {
        when(juditService.processos(CNPJ)).thenReturn(null);

        assertThatThrownBy(() -> service.exportar(CNPJ, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
