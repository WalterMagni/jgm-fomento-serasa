package com.portal.serasa.application.service.liberacao;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Texto de uma Análise de Risco (AR) <b>anonimizada</b>, com o mesmo layout que o PDFBox devolve do
 * relatório real: cedente, sacados, CNPJs e valores aqui são inventados.
 *
 * <p>Cada coluna tem um valor diferente de propósito: se o parser trocar duas colunas de lugar, o
 * teste percebe.</p>
 *
 * <p>Sacados do fixture (face da proposta; a soma bate com a linha TOTAL):</p>
 * <ul>
 *   <li>BETA, CNPJ 11.222.333/0001-81: 0 títulos (fica de fora do card)</li>
 *   <li>GAMA, CNPJ 11.444.777/0001-61: 1 título, 6.000,00</li>
 *   <li>DELTA, CNPJ 45.723.174/0001-10: 1 título, 9.300,00, linha sem código interno</li>
 *   <li>FULANO, CPF 529.982.247-25: 1 título, 6.000,00</li>
 * </ul>
 */
final class PropostaArTextos {

    private PropostaArTextos() {
    }

    static final String TITULO = "JGM LP FUNDO DE INVESTIMENTO EM DIREITOS CREDITORIOS MULTISSETORIAL    Página 001/001";
    static final String CLIENTE = "CLIENTE  :4821 - ACME EMBALAGENS LTDA";
    static final String DATA = "DATA     : 08/10/2026 LIMITES";
    static final String HORARIO = "HORARIO  : 15:59:13 INDIVIDUAL GRUPO";
    static final String GRUPO_VAZIO = "GRUPO    : ----------------------------------- 120.500,00 0,00";
    static final String OPERACAO = "3 28,50 21.300,00 745,25 20.554,75 4 25.800,00";
    static final String RISCO = "35,2500 0,0000 1,1000 41,7500 0,0000 1,3500";
    static final String RODAPE = "ana008r16 USUARIA FICTICIA DA SILVA 08/10/2026 - 16:06:05";

    static final String COLUNAS_SACADOS = "Sacado Qtd Face Vencidos À Vencer Abertos Liquidados Recomprados";

    static final String DOC_BETA = "11.222.333/0001-81";
    static final String LINHA_BETA = "00068 BETA COMERCIAL LTDA 0 0,00 0,00 5.000,00 5.000,00 32.000,00 0,00";
    static final String DOC_GAMA = "11.444.777/0001-61";
    static final String LINHA_GAMA = "00046 GAMA DISTRIBUIDORA S/A 1 6.000,00 800,00 7.200,00 8.000,00 120.000,00 1.500,00";
    static final String DOC_DELTA = "45.723.174/0001-10";
    /** Sem o código interno na frente do nome. */
    static final String LINHA_DELTA = "DELTA ATACADO LTDA 1 9.300,00 0,00 4.100,00 4.100,00 45.500,00 0,00";
    static final String DOC_FULANO = "529.982.247-25";
    static final String LINHA_FULANO = "00091 FULANO DE TAL 1 6.000,00 250,00 0,00 250,00 3.000,00 600,00";
    static final String TOTAL_SACADOS = "TOTAL 3 21.300,00 1.050,00 16.300,00 17.350,00 200.500,00 2.100,00";

    /** Documento + linha de cada sacado, na ordem do relatório, e a linha TOTAL no fim. */
    static List<String> sacadosPadrao() {
        return List.of(DOC_BETA, LINHA_BETA, DOC_GAMA, LINHA_GAMA, DOC_DELTA, LINHA_DELTA, DOC_FULANO, LINHA_FULANO,
                TOTAL_SACADOS);
    }

    /** AR completa e consistente (somas conferem). */
    static String ar() {
        return ar(CLIENTE, HORARIO, GRUPO_VAZIO, OPERACAO, RISCO, sacadosPadrao());
    }

    /**
     * Monta a AR. Parâmetro nulo omite a linha (ou, em {@code risco} e {@code sacados}, o bloco
     * inteiro, título da seção incluído).
     */
    static String ar(String cliente, String horario, String grupo, String operacao, String risco, List<String> sacados) {
        List<String> linhas = new ArrayList<>();
        linhas.add(TITULO);
        linhas.add("ANÁLISE DE RISCO - AR");
        linhas.add("ANÁLISE DO CLIENTE");
        adicionar(linhas, cliente);
        linhas.add(DATA);
        adicionar(linhas, horario);
        adicionar(linhas, grupo);
        if (operacao != null) {
            linhas.add("QTD LIBERADOS P.M. LIBERADOS FACE LIBERADOS DESCONTO LÍQUIDO QTD TOTAL VALOR TOTAL");
            linhas.add(operacao);
        }
        linhas.addAll(carteira());
        if (risco != null) {
            linhas.add("ANÁLISE DE RISCO");
            linhas.add("ATUAL APÓS COMPRA PROPOSTA");
            linhas.add("% COMPROMETIMENTO LIMITE % CONCENTRAÇÃO % COMPROMETIMENTO LIMITE % CONCENTRAÇÃO");
            linhas.add("INDIVIDUAL GRUPO NO TOTAL INDIVIDUAL GRUPO NO TOTAL");
            linhas.add(risco);
        }
        if (sacados != null) {
            linhas.add("ANÁLISE DOS SACADOS");
            linhas.add(COLUNAS_SACADOS);
            linhas.addAll(sacados);
        }
        linhas.add(RODAPE);
        return String.join("\n", linhas);
    }

    /** Posição sintética: liquidados, recomprados, vencidos e a vencer, cada um com o seu TOTAL. */
    static List<String> carteira() {
        return List.of(
                "POSIÇÃO SINTÉTICA DA CARTEIRA - COMPORTAMENTAL - COM STATUS DE CONFIRMAÇÃO",
                "LIQUIDADOS A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "ANTECIPADO 10.000,00 200.000,00 0,00 210.000,00",
                "NO VENCIMENTO 20.000,00 300.000,00 5.000,00 325.000,00",
                "EM ATRASO 3.000,00 9.000,00 0,00 12.000,00",
                "CARTÓRIO 0,00 0,00 0,00 0,00",
                "PROTESTADO 0,00 0,00 0,00 0,00",
                "TOTAL 33.000,00 509.000,00 5.000,00 547.000,00",
                "RECOMPRADOS A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "ÚLTIMOS 10 DIAS 1.000,00 0,00 0,00 1.000,00",
                "DE 11 A 30 DIAS 6.000,00 600,00 400,00 7.000,00",
                "TOTAL 7.000,00 600,00 400,00 8.000,00",
                "TOTAL LIQUIDADO 40.000,00 509.600,00 5.400,00 555.000,00",
                "VENCIDOS A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "ATÉ 10 DIAS 100,00 200,00 0,00 300,00",
                "ACIMA DE 10 DIAS 1.000,00 2.000,00 0,00 3.000,00",
                "TOTAL 1.100,00 2.200,00 0,00 3.300,00",
                "A VENCER A CONFIRMAR CONFIRMADOS NÃO CONFIRMADOS TOTAL",
                "HOJE 0,00 4.000,00 0,00 4.000,00",
                "ATÉ 30 DIAS 15.000,00 21.000,00 0,00 36.000,00",
                "TOTAL 15.000,00 25.000,00 0,00 40.000,00",
                "TOTAL EM ABERTO 16.100,00 27.200,00 0,00 43.300,00");
    }

    private static void adicionar(List<String> linhas, String linha) {
        if (linha != null) {
            linhas.add(linha);
        }
    }

    /**
     * PDF de verdade (Courier 8pt, uma linha de texto por linha do argumento) para exercitar
     * {@code PropostaArParser.ler(byte[])}. Pagina a cada {@code linhasPorPagina} linhas.
     */
    static byte[] pdf(String texto, int linhasPorPagina) {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PDType1Font fonte = new PDType1Font(Standard14Fonts.FontName.COURIER);
            String[] linhas = texto.split("\\R");
            for (int inicio = 0; inicio < linhas.length; inicio += linhasPorPagina) {
                PDPage pagina = new PDPage(PDRectangle.A4);
                documento.addPage(pagina);
                try (PDPageContentStream conteudo = new PDPageContentStream(documento, pagina)) {
                    conteudo.beginText();
                    conteudo.setFont(fonte, 8);
                    conteudo.setLeading(10);
                    conteudo.newLineAtOffset(20, 810);
                    for (int i = inicio; i < Math.min(linhas.length, inicio + linhasPorPagina); i++) {
                        conteudo.showText(linhas[i]);
                        conteudo.newLine();
                    }
                    conteudo.endText();
                }
            }
            documento.save(saida);
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException(erro);
        }
    }
}
