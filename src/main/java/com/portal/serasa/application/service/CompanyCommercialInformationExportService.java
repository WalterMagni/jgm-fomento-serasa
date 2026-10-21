package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.CompanyCommercialInformationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Planilha das fichas comerciais no modelo do time: ficha vertical, uma coluna por parceiro (a
 * mais recente à direita), para printar e colar no comitê. Um bloco por empresa, em ordem
 * alfabética, um embaixo do outro. Valor vencido e a data dele saem em vermelho.
 *
 * <p>Os campos da ficha são texto no formato brasileiro ("1.700.000,00", "17/07/2026"); aqui viram
 * número e data de verdade, para o Excel somar e ordenar. O que não converte vai como texto.</p>
 */
@Service
@RequiredArgsConstructor
public class CompanyCommercialInformationExportService {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private enum Tipo { TEXTO, DATA, MOEDA, PERCENTUAL, LONGO }

    private record Linha(String rotulo, Tipo tipo, Function<CompanyCommercialInformationEntity, String> valor, boolean vencido) {

        static Linha secao(String rotulo) {
            return new Linha(rotulo, null, null, false);
        }

        static Linha campo(String rotulo, Tipo tipo, Function<CompanyCommercialInformationEntity, String> valor) {
            return new Linha(rotulo, tipo, valor, false);
        }

        static Linha vencido(String rotulo, Tipo tipo, Function<CompanyCommercialInformationEntity, String> valor) {
            return new Linha(rotulo, tipo, valor, true);
        }

        boolean ehSecao() {
            return tipo == null;
        }
    }

    private static final List<Linha> LINHAS = List.of(
            Linha.campo("Data", Tipo.DATA, CompanyCommercialInformationEntity::getOperationDate),
            Linha.campo("Tipo", Tipo.TEXTO, CompanyCommercialInformationEntity::getOperationType),
            Linha.campo("Cliente desde", Tipo.DATA, CompanyCommercialInformationEntity::getCustomerSince),
            Linha.campo("Ultima Operação", Tipo.DATA, CompanyCommercialInformationEntity::getLastOperationDate),
            Linha.campo("Limite", Tipo.MOEDA, CompanyCommercialInformationEntity::getCreditLimit),
            Linha.secao("Riscos"),
            Linha.campo("Risco Dupl", Tipo.MOEDA, CompanyCommercialInformationEntity::getDuplicateRisk),
            Linha.campo("Vencimento da última duplicata", Tipo.DATA, CompanyCommercialInformationEntity::getDuplicateDueDate),
            Linha.campo("Risco Comissária", Tipo.MOEDA, CompanyCommercialInformationEntity::getCommissionRisk),
            // Ainda não existe campo de vencimento para comissária/cheque no cadastro: a linha vai
            // vazia para a ficha bater com o modelo do time.
            Linha.campo("Vencimento da última Comissária", Tipo.TEXTO, ficha -> null),
            Linha.campo("Risco Cheque", Tipo.MOEDA, CompanyCommercialInformationEntity::getCheckRisk),
            Linha.campo("Vencimento do última Cheque", Tipo.TEXTO, ficha -> null),
            Linha.vencido("Valor vencido", Tipo.MOEDA, CompanyCommercialInformationEntity::getOverdueValue),
            Linha.vencido("Data do vencido", Tipo.DATA, CompanyCommercialInformationEntity::getOverdueDate),
            Linha.secao("Performance"),
            Linha.campo("VOP", Tipo.MOEDA, CompanyCommercialInformationEntity::getVop),
            Linha.campo("L1 (Pontual)", Tipo.PERCENTUAL, CompanyCommercialInformationEntity::getPunctualPercentage),
            Linha.campo("L2 (Atraso)", Tipo.PERCENTUAL, CompanyCommercialInformationEntity::getDelayPercentage),
            Linha.campo("L3 (Cartório)", Tipo.PERCENTUAL, CompanyCommercialInformationEntity::getNotaryPercentage),
            Linha.campo("L4 (Recompra)", Tipo.PERCENTUAL, CompanyCommercialInformationEntity::getRepurchasePercentage),
            Linha.campo("Observação", Tipo.LONGO, CompanyCommercialInformationEntity::getNotes));

    private final CompanyCommercialInformationJpaRepository commercialInformationJpaRepository;
    private final ClientJpaRepository clientJpaRepository;

    @Transactional(readOnly = true)
    public byte[] exportar(Collection<UUID> ids) {
        List<CompanyCommercialInformationEntity> fichas = commercialInformationJpaRepository.findAllById(ids);
        Map<UUID, ClientEntity> empresas = clientJpaRepository.findAllById(
                        fichas.stream().map(CompanyCommercialInformationEntity::getClientId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(ClientEntity::getId, Function.identity()));

        Map<String, List<CompanyCommercialInformationEntity>> blocos = fichas.stream()
                .collect(Collectors.groupingBy(ficha -> titulo(empresas.get(ficha.getClientId())), LinkedHashMap::new, Collectors.toList()));

        try (XSSFWorkbook planilha = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(planilha);
            Sheet aba = planilha.createSheet("Informações comerciais");
            int maiorBloco = 1;
            int linha = 0;
            List<String> titulos = blocos.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
            for (String titulo : titulos) {
                List<CompanyCommercialInformationEntity> doBloco = blocos.get(titulo).stream()
                        .sorted(Comparator.comparing(ficha -> data(ficha.getOperationDate()), Comparator.nullsFirst(Comparator.naturalOrder())))
                        .toList();
                if (linha > 0) {
                    linha++;
                }
                linha = bloco(aba, linha, titulo, doBloco, estilos);
                maiorBloco = Math.max(maiorBloco, doBloco.size());
            }
            aba.setColumnWidth(0, 34 * 256);
            for (int coluna = 1; coluna <= maiorBloco; coluna++) {
                aba.setColumnWidth(coluna, 20 * 256);
            }
            planilha.write(saida);
            return saida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Falha ao gerar a planilha de informações comerciais", ex);
        }
    }

    /** Escreve um bloco a partir de {@code inicio} e devolve a próxima linha livre. */
    private static int bloco(Sheet aba, int inicio, String titulo, List<CompanyCommercialInformationEntity> fichas, Estilos estilos) {
        int ultimaColuna = fichas.size();
        int linha = inicio;

        faixa(aba, linha++, titulo, ultimaColuna, estilos.titulo);

        Row cabecalho = aba.createRow(linha++);
        celula(cabecalho, 0, null, estilos.rotulo);
        for (int i = 0; i < fichas.size(); i++) {
            String parceiro = fichas.get(i).getPartner();
            celula(cabecalho, i + 1, parceiro == null || parceiro.isBlank() ? "Nome do Parceiro" : parceiro, estilos.parceiro);
        }

        for (Linha definicao : LINHAS) {
            if (definicao.ehSecao()) {
                faixa(aba, linha++, definicao.rotulo(), ultimaColuna, estilos.secao);
                continue;
            }
            Row row = aba.createRow(linha++);
            celula(row, 0, definicao.rotulo(), estilos.rotulo);
            for (int i = 0; i < fichas.size(); i++) {
                CompanyCommercialInformationEntity ficha = fichas.get(i);
                boolean vermelho = definicao.vencido() && temVencido(ficha);
                valor(row, i + 1, definicao.tipo(), definicao.valor().apply(ficha), vermelho, estilos);
            }
        }
        return linha;
    }

    private static void faixa(Sheet aba, int linha, String texto, int ultimaColuna, CellStyle estilo) {
        Row row = aba.createRow(linha);
        for (int coluna = 0; coluna <= ultimaColuna; coluna++) {
            celula(row, coluna, coluna == 0 ? texto : null, estilo);
        }
        if (ultimaColuna > 0) {
            aba.addMergedRegion(new CellRangeAddress(linha, linha, 0, ultimaColuna));
        }
    }

    private static void valor(Row row, int coluna, Tipo tipo, String texto, boolean vermelho, Estilos estilos) {
        Cell cell = row.createCell(coluna);
        String limpo = texto == null ? "" : texto.trim();
        switch (tipo) {
            case MOEDA -> {
                BigDecimal numero = numero(limpo);
                if (numero != null && numero.signum() != 0) {
                    cell.setCellValue(numero.doubleValue());
                } else if (numero == null && !limpo.isEmpty()) {
                    cell.setCellValue(limpo);
                }
                cell.setCellStyle(vermelho ? estilos.moedaVencida : estilos.moeda);
            }
            case PERCENTUAL -> {
                BigDecimal numero = numero(limpo);
                if (numero != null) {
                    cell.setCellValue(numero.movePointLeft(2).doubleValue());
                } else if (!limpo.isEmpty()) {
                    cell.setCellValue(limpo);
                }
                cell.setCellStyle(estilos.percentual);
            }
            case DATA -> {
                LocalDate data = data(limpo);
                if (data != null) {
                    cell.setCellValue(data);
                } else if (!limpo.isEmpty()) {
                    cell.setCellValue(limpo);
                }
                cell.setCellStyle(vermelho ? estilos.dataVencida : estilos.data);
            }
            case LONGO -> {
                if (!limpo.isEmpty()) {
                    cell.setCellValue(limpo.length() > 32000 ? limpo.substring(0, 32000) + "…" : limpo);
                }
                cell.setCellStyle(estilos.longo);
            }
            case TEXTO -> {
                if (!limpo.isEmpty()) {
                    cell.setCellValue(limpo);
                }
                cell.setCellStyle(estilos.texto);
            }
        }
    }

    private static void celula(Row row, int coluna, String texto, CellStyle estilo) {
        Cell cell = row.createCell(coluna);
        if (texto != null) {
            cell.setCellValue(texto);
        }
        cell.setCellStyle(estilo);
    }

    private static boolean temVencido(CompanyCommercialInformationEntity ficha) {
        BigDecimal vencido = numero(ficha.getOverdueValue());
        return vencido != null && vencido.signum() > 0;
    }

    /** "1.700.000,00" ou "R$ 1.700.000,00" → 1700000.00; nulo quando vazio ou não é número. */
    static BigDecimal numero(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String digitos = texto.replace(".", "").replace(",", ".").replaceAll("[^\\d.-]", "");
        if (digitos.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(digitos);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** "17/07/2026" ou "2026-07-17"; nulo fora desses formatos. */
    static LocalDate data(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return texto.contains("/") ? LocalDate.parse(texto.trim(), DATA_BR) : LocalDate.parse(texto.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String titulo(ClientEntity empresa) {
        if (empresa == null) {
            return "Empresa sem cadastro";
        }
        String nome = empresa.getName() == null || empresa.getName().isBlank() ? "Empresa sem nome" : empresa.getName().trim();
        String cnpj = empresa.getDocumentNumber();
        if (cnpj == null || cnpj.length() != 14) {
            return nome;
        }
        return nome + " · " + cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." + cnpj.substring(5, 8) + "/"
                + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }

    /** Estilos criados uma vez por planilha: o Excel tem limite de estilos por arquivo. Cores do modelo do time. */
    private static final class Estilos {
        private static final byte[] AZUL_ESCURO = {0x1F, 0x38, 0x64};
        private static final byte[] AZUL_SECAO = {(byte) 0xBD, (byte) 0xD7, (byte) 0xEE};
        private static final byte[] AZUL_ROTULO = {(byte) 0xDD, (byte) 0xEB, (byte) 0xF7};
        private static final byte[] BRANCO = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        private static final byte[] ROSA_VENCIDO = {(byte) 0xFC, (byte) 0xE4, (byte) 0xE4};
        private static final byte[] VERMELHO = {(byte) 0xC0, 0x00, 0x00};

        final CellStyle titulo;
        final CellStyle secao;
        final CellStyle rotulo;
        final CellStyle parceiro;
        final CellStyle texto;
        final CellStyle longo;
        final CellStyle moeda;
        final CellStyle data;
        final CellStyle percentual;
        final CellStyle moedaVencida;
        final CellStyle dataVencida;

        Estilos(XSSFWorkbook planilha) {
            var formatos = planilha.createDataFormat();
            short formatoMoeda = formatos.getFormat("\"R$\" #,##0.00");
            short formatoData = formatos.getFormat("dd/mm/yyyy");

            titulo = estilo(planilha, AZUL_ESCURO, fonte(planilha, BRANCO, true), HorizontalAlignment.CENTER);
            secao = estilo(planilha, AZUL_SECAO, fonte(planilha, AZUL_ESCURO, true), HorizontalAlignment.LEFT);
            rotulo = estilo(planilha, AZUL_ROTULO, fonte(planilha, AZUL_ESCURO, true), HorizontalAlignment.LEFT);
            parceiro = estilo(planilha, BRANCO, fonte(planilha, AZUL_ESCURO, true), HorizontalAlignment.CENTER);
            texto = estilo(planilha, null, null, HorizontalAlignment.CENTER);
            longo = estilo(planilha, null, null, HorizontalAlignment.LEFT);
            longo.setWrapText(true);
            longo.setVerticalAlignment(VerticalAlignment.TOP);
            moeda = estilo(planilha, null, null, HorizontalAlignment.CENTER);
            moeda.setDataFormat(formatoMoeda);
            data = estilo(planilha, null, null, HorizontalAlignment.CENTER);
            data.setDataFormat(formatoData);
            percentual = estilo(planilha, null, null, HorizontalAlignment.CENTER);
            percentual.setDataFormat(formatos.getFormat("0.00%"));

            XSSFFont fonteVencido = fonte(planilha, VERMELHO, true);
            moedaVencida = estilo(planilha, ROSA_VENCIDO, fonteVencido, HorizontalAlignment.CENTER);
            moedaVencida.setDataFormat(formatoMoeda);
            dataVencida = estilo(planilha, ROSA_VENCIDO, fonteVencido, HorizontalAlignment.CENTER);
            dataVencida.setDataFormat(formatoData);
        }

        private static XSSFFont fonte(XSSFWorkbook planilha, byte[] cor, boolean negrito) {
            XSSFFont fonte = planilha.createFont();
            fonte.setColor(new XSSFColor(cor, null));
            fonte.setBold(negrito);
            return fonte;
        }

        private static XSSFCellStyle estilo(XSSFWorkbook planilha, byte[] fundo, XSSFFont fonte, HorizontalAlignment alinhamento) {
            XSSFCellStyle estilo = planilha.createCellStyle();
            if (fundo != null) {
                estilo.setFillForegroundColor(new XSSFColor(fundo, null));
                estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (fonte != null) {
                estilo.setFont(fonte);
            }
            estilo.setAlignment(alinhamento);
            estilo.setVerticalAlignment(VerticalAlignment.CENTER);
            estilo.setBorderTop(BorderStyle.THIN);
            estilo.setBorderBottom(BorderStyle.THIN);
            estilo.setBorderLeft(BorderStyle.THIN);
            estilo.setBorderRight(BorderStyle.THIN);
            return estilo;
        }
    }
}
