package com.portal.serasa.application.service.judit;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Planilha dos processos da Judit que a tela está mostrando. Não chama a Judit: lê a última lista
 * guardada, então exportar não custa nada.
 *
 * <p>Duas abas: os processos (um por número CNJ, já com as instâncias juntas) e um resumo com os
 * totais e a explicação de por que o número da Judit não bate com o da lista.</p>
 */
@Service
@RequiredArgsConstructor
public class JuditExportService {

    private static final byte[] BORDO = {(byte) 0x61, (byte) 0x20, (byte) 0x35};
    private static final DateTimeFormatter DATA_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] COLUNAS = {
            "Processo (CNJ)", "Instâncias", "Polo da empresa", "Outra parte (autor se a empresa é ré)", "Autuação", "Classe", "Assuntos",
            "Área", "Tribunal", "Órgão julgador", "Cidade", "UF", "Valor da causa", "Distribuído em",
            "Último andamento em", "Último andamento", "Segredo de justiça", "Atualizado na Judit em"};
    private static final int[] LARGURAS = {26, 11, 13, 40, 45, 32, 40, 26, 10, 36, 18, 5, 16, 13, 13, 50, 10, 13};

    private final JuditService juditService;

    public record Planilha(String nome, byte[] conteudo) {
    }

    /**
     * @param cnjs processos a exportar, na ordem da tela (filtro e ordenação já aplicados); nulo ou
     *             vazio exporta a lista inteira na ordem de distribuição
     */
    public Planilha exportar(String cnpj, Collection<String> cnjs) {
        JuditService.ProcessosConsultados consulta = juditService.processos(cnpj);
        if (consulta == null) {
            throw new IllegalArgumentException("Esta empresa ainda não tem lista de processos da Judit para exportar.");
        }
        JuditLeitura.Processos dados = consulta.dados();
        List<JuditLeitura.Processo> processos;
        if (cnjs == null || cnjs.isEmpty()) {
            processos = dados.lista();
        } else {
            Map<String, JuditLeitura.Processo> porCnj = new HashMap<>();
            dados.lista().forEach(p -> {
                if (p.cnj() != null) {
                    porCnj.putIfAbsent(p.cnj(), p);
                }
            });
            processos = cnjs.stream().distinct().map(porCnj::get).filter(Objects::nonNull).toList();
        }

        try (XSSFWorkbook planilha = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(planilha);
            abaProcessos(planilha, estilos, processos);
            abaResumo(planilha, estilos, cnpj, consulta, processos.size());
            planilha.write(saida);
            String documento = cnpj.replaceAll("\\D", "");
            return new Planilha("processos-judit-" + documento + "-" + LocalDate.now().format(DATA_ARQUIVO) + ".xlsx",
                    saida.toByteArray());
        } catch (IOException erro) {
            throw new UncheckedIOException("Falha ao montar a planilha", erro);
        }
    }

    private static void abaProcessos(XSSFWorkbook planilha, Estilos estilos, List<JuditLeitura.Processo> processos) {
        Sheet aba = planilha.createSheet("Processos");
        Row topo = aba.createRow(0);
        topo.setHeightInPoints(30);
        for (int i = 0; i < COLUNAS.length; i++) {
            Cell cell = topo.createCell(i);
            cell.setCellValue(COLUNAS[i]);
            cell.setCellStyle(estilos.cabecalho);
            aba.setColumnWidth(i, LARGURAS[i] * 256);
        }
        aba.createFreezePane(0, 1);

        int linha = 1;
        for (JuditLeitura.Processo p : processos) {
            Row row = aba.createRow(linha++);
            int c = 0;
            texto(row, c++, p.cnj(), estilos.texto);
            texto(row, c++, String.join(", ", p.instancias().stream().map(i -> i + "ª").toList()), estilos.texto);
            texto(row, c++, polo(p.polo()), estilos.texto);
            texto(row, c++, String.join("; ", p.contrapartes()), estilos.quebra);
            texto(row, c++, p.autuacao(), estilos.quebra);
            texto(row, c++, p.classe(), estilos.quebra);
            texto(row, c++, String.join("; ", p.assuntos()), estilos.quebra);
            texto(row, c++, p.area(), estilos.texto);
            texto(row, c++, p.tribunal(), estilos.texto);
            texto(row, c++, String.join("; ", p.orgaos()), estilos.quebra);
            texto(row, c++, p.cidade(), estilos.texto);
            texto(row, c++, p.uf(), estilos.texto);
            moeda(row, c++, p.valor(), estilos);
            data(row, c++, p.distribuidoEm(), estilos);
            data(row, c++, p.ultimoAndamento() == null ? null : p.ultimoAndamento().data(), estilos);
            texto(row, c++, p.ultimoAndamento() == null ? null : p.ultimoAndamento().texto(), estilos.quebra);
            texto(row, c++, p.emSegredo() ? "sim" : "", estilos.texto);
            data(row, c, p.fonteAtualizadaEm(), estilos);
        }
        aba.setAutoFilter(new CellRangeAddress(0, Math.max(0, linha - 1), 0, COLUNAS.length - 1));
    }

    private static void abaResumo(XSSFWorkbook planilha, Estilos estilos, String cnpj,
                                  JuditService.ProcessosConsultados consulta, int exportados) {
        JuditLeitura.Processos dados = consulta.dados();
        Sheet aba = planilha.createSheet("Resumo");
        aba.setColumnWidth(0, 34 * 256);
        aba.setColumnWidth(1, 90 * 256);

        List<Object[]> linhas = new ArrayList<>(List.of(
                new Object[]{"CNPJ", cnpj.replaceAll("\\D", "")},
                new Object[]{"Fonte", "Judit — base histórica (Data Lake)"},
                new Object[]{"Consultado em", consulta.consultadoEm().toLocalDate()},
                new Object[]{"Consultado por", consulta.consultadoPor()},
                new Object[]{"Processos na lista", dados.quantidade()},
                new Object[]{"Processos nesta planilha", exportados},
                new Object[]{"Como ré", dados.comoReu()},
                new Object[]{"Como autora", dados.comoAutor()},
                new Object[]{"Valor das causas como ré", dados.valorComoReu()},
                new Object[]{"Ré por título contestado", dados.tituloContestadoComoReu()},
                new Object[]{"Registros devolvidos pela Judit", dados.registros()},
                new Object[]{"Registros no resumo da Judit", consulta.totalNoResumo()},
                new Object[]{"Por que os números diferem",
                        "A Judit conta um registro por instância: o mesmo processo em 1ª e 2ª instância soma dois. "
                                + "A lista junta as instâncias pelo número CNJ. No recurso a empresa pode trocar de "
                                + "polo (ré na 1ª, recorrente na 2ª); a lista conta como ré."}));
        if (consulta.somenteReu()) {
            linhas.add(new Object[]{"Atenção",
                    "A empresa passa de 1.000 registros; a Judit corta nesse número, então a busca pediu só os processos em que ela é ré."});
        }

        for (int i = 0; i < linhas.size(); i++) {
            Row row = aba.createRow(i);
            texto(row, 0, (String) linhas.get(i)[0], estilos.rotulo);
            Object valor = linhas.get(i)[1];
            if (valor instanceof BigDecimal decimal) {
                moeda(row, 1, decimal, estilos);
            } else if (valor instanceof Integer inteiro) {
                Cell cell = row.createCell(1);
                cell.setCellValue(inteiro);
                cell.setCellStyle(estilos.inteiro);
            } else if (valor instanceof LocalDate dia) {
                data(row, 1, dia, estilos);
            } else {
                texto(row, 1, valor == null ? null : valor.toString(), estilos.quebra);
            }
        }
    }

    private static String polo(String polo) {
        return switch (polo) {
            case JuditLeitura.REU -> "Ré";
            case JuditLeitura.AUTOR -> "Autora";
            case JuditLeitura.TERCEIRO -> "Terceira";
            default -> "Sem polo";
        };
    }

    // ── células ─────────────────────────────────────────────────────────────────────────────────

    private static final class Estilos {
        final CellStyle cabecalho;
        final CellStyle rotulo;
        final CellStyle texto;
        final CellStyle quebra;
        final CellStyle moeda;
        final CellStyle data;
        final CellStyle inteiro;

        Estilos(XSSFWorkbook planilha) {
            Font branco = planilha.createFont();
            branco.setBold(true);
            branco.setColor(IndexedColors.WHITE.getIndex());
            XSSFCellStyle topo = planilha.createCellStyle();
            topo.setFont(branco);
            topo.setFillForegroundColor(new XSSFColor(BORDO, null));
            topo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            topo.setVerticalAlignment(VerticalAlignment.CENTER);
            topo.setWrapText(true);
            topo.setBorderBottom(BorderStyle.THIN);
            cabecalho = topo;

            Font negrito = planilha.createFont();
            negrito.setBold(true);
            rotulo = planilha.createCellStyle();
            rotulo.setFont(negrito);
            rotulo.setVerticalAlignment(VerticalAlignment.TOP);

            texto = planilha.createCellStyle();
            texto.setVerticalAlignment(VerticalAlignment.TOP);
            quebra = planilha.createCellStyle();
            quebra.setVerticalAlignment(VerticalAlignment.TOP);
            quebra.setWrapText(true);

            var formatos = planilha.createDataFormat();
            moeda = planilha.createCellStyle();
            moeda.setDataFormat(formatos.getFormat("\"R$\" #,##0.00"));
            moeda.setVerticalAlignment(VerticalAlignment.TOP);
            data = planilha.createCellStyle();
            data.setDataFormat(formatos.getFormat("dd/mm/yyyy"));
            data.setVerticalAlignment(VerticalAlignment.TOP);
            inteiro = planilha.createCellStyle();
            inteiro.setDataFormat(formatos.getFormat("0"));
            inteiro.setVerticalAlignment(VerticalAlignment.TOP);
        }
    }

    private static void texto(Row row, int coluna, String valor, CellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (valor != null && !valor.isBlank()) {
            cell.setCellValue(valor.length() > 32000 ? valor.substring(0, 32000) + "…" : valor);
        }
        cell.setCellStyle(estilo);
    }

    private static void moeda(Row row, int coluna, BigDecimal valor, Estilos estilos) {
        Cell cell = row.createCell(coluna);
        if (valor != null) {
            cell.setCellValue(valor.doubleValue());
        }
        cell.setCellStyle(estilos.moeda);
    }

    private static void data(Row row, int coluna, LocalDate valor, Estilos estilos) {
        Cell cell = row.createCell(coluna);
        if (valor != null) {
            cell.setCellValue(valor);
        }
        cell.setCellStyle(estilos.data);
    }
}
