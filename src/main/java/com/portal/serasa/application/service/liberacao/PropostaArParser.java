package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.model.liberacao.CarteiraSacado;
import com.portal.serasa.domain.model.liberacao.PropostaAr;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê o PDF "ANÁLISE DE RISCO - AR" que o sistema de operações gera para cada proposta.
 *
 * <p>O relatório é texto (não imagem), e o PDFBox ordenado por posição devolve uma linha por
 * linha da tabela. A leitura anda linha a linha com um estado de seção (liquidados, recomprados,
 * vencidos, a vencer, risco, sacados), porque os rótulos "TOTAL" se repetem em todas. Linha que
 * não casa com nada — cabeçalho de página, rodapé, título de coluna repetido na página seguinte
 * — é ignorada.</p>
 *
 * <p>Na tabela de sacados cada sacado ocupa duas linhas: o documento sozinho, e embaixo o código
 * interno, o nome e os números. A linha TOTAL do fim permite conferir se nada se perdeu.</p>
 */
@Component
public class PropostaArParser {

    /** Sacado como saiu do relatório. {@code face} é o valor dele nesta proposta. */
    public record SacadoLido(String documento, String nome, BigDecimal face, CarteiraSacado carteira) {
    }

    /** {@code totalTitulos}/{@code totalFace}: linha TOTAL da tabela de sacados, nula se não veio. */
    public record Leitura(String clienteCodigo, String clienteNome, PropostaAr resumo,
                          List<SacadoLido> sacados, Integer totalTitulos, BigDecimal totalFace) {
    }

    private enum Secao { CLIENTE, LIQUIDADOS, RECOMPRADOS, VENCIDOS, VINCENDOS, RISCO, SACADOS }

    private static final String NUM = "(-?[\\d.]*\\d,\\d+)";
    private static final String SEP = "\\s+";

    private static final Pattern CLIENTE = Pattern.compile("^CLIENTE\\s*:\\s*(\\d+)\\s*-\\s*(.+?)\\s*$");
    private static final Pattern DATA = Pattern.compile("^DATA\\s*:\\s*(\\d{2})/(\\d{2})/(\\d{4})");
    private static final Pattern HORARIO = Pattern.compile("^HORARIO\\s*:\\s*(\\d{2}:\\d{2}(?::\\d{2})?)");
    private static final Pattern GRUPO = Pattern.compile("^GRUPO\\s*:\\s*(.*?)\\s*" + NUM + SEP + NUM + "\\s*$");
    private static final Pattern OPERACAO = Pattern.compile(
            "^(\\d+)" + SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + SEP + "(\\d+)" + SEP + NUM + "\\s*$");
    private static final Pattern QUATRO = Pattern.compile(
            "^(.+?)" + SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + "\\s*$");
    private static final Pattern RISCO = Pattern.compile(
            "^" + NUM + SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + "\\s*$");
    private static final Pattern DOCUMENTO = Pattern.compile(
            "^(\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}|\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2})\\s*$");
    /** Face, vencidos, a vencer, abertos, liquidados, recomprados. */
    private static final String SEIS = SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + SEP + NUM + "\\s*$";
    private static final Pattern SACADO = Pattern.compile("^(?:\\d{3,6}\\s+)?(.+?)" + SEP + "(\\d+)" + SEIS);
    private static final Pattern TOTAL_SACADOS = Pattern.compile("^TOTAL" + SEP + "(\\d+)" + SEIS);

    public Leitura ler(byte[] pdf) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return lerTexto(stripper.getText(documento));
        } catch (IOException erro) {
            throw new IllegalArgumentException("Não foi possível ler o PDF. Confira se o arquivo abre normalmente.", erro);
        }
    }

    Leitura lerTexto(String texto) {
        String clienteCodigo = null;
        String clienteNome = null;
        String data = null;
        String hora = null;
        String grupo = null;
        BigDecimal limiteIndividual = null;
        BigDecimal limiteGrupo = null;
        Integer qtdLiberados = null;
        BigDecimal prazoMedio = null;
        BigDecimal faceLiberados = null;
        BigDecimal desconto = null;
        BigDecimal liquido = null;
        Integer qtdTotal = null;
        BigDecimal valorTotal = null;
        BigDecimal liquidados = null;
        BigDecimal liquidadosEmAtraso = null;
        BigDecimal recomprados = null;
        BigDecimal vencidos = null;
        BigDecimal vincendos = null;
        BigDecimal emAberto = null;
        BigDecimal[] risco = null;
        List<SacadoLido> sacados = new ArrayList<>();
        Integer totalTitulos = null;
        BigDecimal totalFace = null;
        boolean ehAr = false;

        Secao secao = Secao.CLIENTE;
        boolean esperandoOperacao = false;
        String documentoPendente = null;

        for (String bruta : texto.split("\\R")) {
            String linha = bruta.trim();
            if (linha.isEmpty()) {
                continue;
            }
            String rotulo = semAcento(linha).toUpperCase();

            if (rotulo.startsWith("ANALISE DE RISCO - AR")) {
                ehAr = true;
                continue;
            }
            if (rotulo.startsWith("ANALISE DOS SACADOS")) {
                secao = Secao.SACADOS;
                continue;
            }
            if (rotulo.equals("ANALISE DE RISCO")) {
                secao = Secao.RISCO;
                continue;
            }
            if (rotulo.contains("A CONFIRMAR") && rotulo.contains("CONFIRMADOS")) {
                if (rotulo.startsWith("LIQUIDADOS")) {
                    secao = Secao.LIQUIDADOS;
                } else if (rotulo.startsWith("RECOMPRADOS")) {
                    secao = Secao.RECOMPRADOS;
                } else if (rotulo.startsWith("VENCIDOS")) {
                    secao = Secao.VENCIDOS;
                } else if (rotulo.startsWith("A VENCER")) {
                    secao = Secao.VINCENDOS;
                }
                continue;
            }

            Matcher m;
            if (secao == Secao.CLIENTE) {
                if ((m = CLIENTE.matcher(linha)).find()) {
                    clienteCodigo = m.group(1);
                    clienteNome = m.group(2);
                } else if ((m = DATA.matcher(linha)).find()) {
                    data = m.group(3) + "-" + m.group(2) + "-" + m.group(1);
                } else if ((m = HORARIO.matcher(linha)).find()) {
                    hora = m.group(1).length() == 5 ? m.group(1) + ":00" : m.group(1);
                } else if ((m = GRUPO.matcher(linha)).find()) {
                    String nome = m.group(1).replaceAll("^-+$", "").trim();
                    grupo = nome.isEmpty() ? null : nome;
                    limiteIndividual = numero(m.group(2));
                    limiteGrupo = numero(m.group(3));
                } else if (rotulo.startsWith("QTD LIBERADOS")) {
                    esperandoOperacao = true;
                } else if (esperandoOperacao && (m = OPERACAO.matcher(linha)).find()) {
                    qtdLiberados = Integer.valueOf(m.group(1));
                    prazoMedio = numero(m.group(2));
                    faceLiberados = numero(m.group(3));
                    desconto = numero(m.group(4));
                    liquido = numero(m.group(5));
                    qtdTotal = Integer.valueOf(m.group(6));
                    valorTotal = numero(m.group(7));
                    esperandoOperacao = false;
                }
                continue;
            }

            if (secao == Secao.RISCO) {
                if ((m = RISCO.matcher(linha)).find()) {
                    risco = new BigDecimal[6];
                    for (int i = 0; i < 6; i++) {
                        risco[i] = numero(m.group(i + 1));
                    }
                }
                continue;
            }

            if (secao == Secao.SACADOS) {
                if ((m = DOCUMENTO.matcher(linha)).find()) {
                    documentoPendente = m.group(1).replaceAll("\\D", "");
                } else if ((m = TOTAL_SACADOS.matcher(rotulo)).find()) {
                    totalTitulos = Integer.valueOf(m.group(1));
                    totalFace = numero(m.group(2));
                } else if (documentoPendente != null && (m = SACADO.matcher(linha)).find()) {
                    sacados.add(new SacadoLido(documentoPendente, m.group(1).trim(), numero(m.group(3)),
                            new CarteiraSacado(Integer.valueOf(m.group(2)), numero(m.group(4)), numero(m.group(5)),
                                    numero(m.group(6)), numero(m.group(7)), numero(m.group(8)))));
                    documentoPendente = null;
                }
                continue;
            }

            // Liquidados, recomprados, vencidos e a vencer: rótulo + 4 colunas, a última é o total.
            if ((m = QUATRO.matcher(linha)).find()) {
                String nome = semAcento(m.group(1)).toUpperCase().trim();
                BigDecimal total = numero(m.group(5));
                if (nome.equals("TOTAL EM ABERTO")) {
                    emAberto = total;
                } else if (nome.equals("TOTAL")) {
                    switch (secao) {
                        case LIQUIDADOS -> liquidados = total;
                        case RECOMPRADOS -> recomprados = total;
                        case VENCIDOS -> vencidos = total;
                        case VINCENDOS -> vincendos = total;
                        default -> { }
                    }
                } else if (nome.equals("EM ATRASO") && secao == Secao.LIQUIDADOS) {
                    liquidadosEmAtraso = total;
                }
            }
        }

        if (!ehAr || (clienteCodigo == null && sacados.isEmpty())) {
            throw new IllegalArgumentException("Este PDF não parece uma Análise de Risco (AR) do sistema de operações.");
        }

        String emitidaEm = data == null ? null : data + "T" + (hora == null ? "00:00:00" : hora);
        PropostaAr resumo = new PropostaAr(
                clienteCodigo, emitidaEm, grupo, limiteIndividual, limiteGrupo,
                qtdLiberados, prazoMedio, faceLiberados, desconto, liquido, qtdTotal, valorTotal,
                liquidados, liquidadosEmAtraso, recomprados, vencidos, vincendos, emAberto,
                risco == null ? null : risco[0], risco == null ? null : risco[1], risco == null ? null : risco[2],
                risco == null ? null : risco[3], risco == null ? null : risco[4], risco == null ? null : risco[5]);
        return new Leitura(clienteCodigo, clienteNome, resumo, List.copyOf(sacados), totalTitulos, totalFace);
    }

    /** "1.234,56" → 1234.56 */
    static BigDecimal numero(String texto) {
        return new BigDecimal(texto.replace(".", "").replace(',', '.'));
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
