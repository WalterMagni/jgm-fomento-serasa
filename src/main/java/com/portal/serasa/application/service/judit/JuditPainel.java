package com.portal.serasa.application.service.judit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Os blocos da aba Processos, no desenho do Vadu (pedido do time, 2026-10-26): cards por justiça,
 * processos por área, polo, por ano, por UF, últimos processos e, por instância, os totais por polo e
 * situação, classes, assuntos, partes e o segmento financeiro.
 *
 * <p>Sai inteiro da lista do Data Lake já guardada: montar o painel não chama a Judit nem cobra. Sem
 * estado e sem banco, como o {@link JuditLeitura}.</p>
 */
public final class JuditPainel {

    public static final String REU = JuditLeitura.REU;
    public static final String AUTOR = JuditLeitura.AUTOR;
    public static final String OUTROS = "OUTROS";

    /** As justiças que a tela sempre mostra, mesmo vazias; superiores e outras só quando há processo. */
    static final List<String> JUSTICAS = List.of("ESTADUAL", "TRABALHO", "FEDERAL", "ELEITORAL", "MILITAR");

    static final int ULTIMOS = 5;

    private JuditPainel() {
    }

    public record Soma(int quantidade, BigDecimal valor) {
        static final Soma ZERO = new Soma(0, BigDecimal.ZERO);
    }

    /** @param area nula quando a Judit não informou */
    public record Area(String area, int quantidade, double porcentagem, LocalDate primeiroProcesso,
                       LocalDate ultimaMovimentacao) {
    }

    /** @param periodo o ano ("2026") ou o mês ("2026-08") */
    public record PorPeriodo(String periodo, int reu, int autor, int outros) {
    }

    /** @param situacao EM_TRAMITACAO, SUSPENSO, ARQUIVADO ou nula (sem andamento) */
    public record PorSituacao(String situacao, int quantidade, BigDecimal valor) {
    }

    public record PorPolo(Soma soma, List<PorSituacao> situacoes, List<JuditLeitura.Contagem> classes,
                          List<JuditLeitura.Contagem> assuntos) {
    }

    /** Uma contraparte: em quantos processos aparece, com a empresa como ré ou como autora, e o valor somado. */
    public record Parte(String nome, int quantidade, int empresaRe, int empresaAutora, BigDecimal valor) {
    }

    public record Ultimo(String cnj, String tribunal, String uf, String classe, String polo, String situacao,
                         LocalDate distribuidoEm, LocalDate ultimaMovimentacao) {
    }

    /**
     * Um recorte da lista — todos os processos, ou os de uma justiça.
     *
     * @param porJustica quantidade e valor de cada justiça, para os cards do topo
     * @param porPolo    REU, AUTOR e OUTROS: soma, situações, classes e assuntos
     */
    public record Bloco(Soma total, Soma comoReu, Soma comoAutor, Soma outros, Map<String, Soma> porJustica,
                        List<Area> areas, List<JuditLeitura.Contagem> porUf, List<PorPeriodo> porAno,
                        List<PorPeriodo> ultimos12Meses, Map<String, PorPolo> porPolo, Soma segmentoFinanceiro,
                        List<Parte> partes, List<Ultimo> ultimos) {

        /** Sem as partes, que podem ser centenas: o card da Visão 360º não mostra. */
        Bloco semPartes() {
            return new Bloco(total, comoReu, comoAutor, outros, porJustica, areas, porUf, porAno, ultimos12Meses,
                    porPolo, segmentoFinanceiro, List.of(), ultimos);
        }
    }

    /** @param porJustica as cinco justiças de sempre, mais superiores e outras quando há processo */
    public record Painel(Bloco geral, Map<String, Bloco> porJustica) {

        /** Para o card: só o geral, sem partes. */
        public Painel soGeral() {
            return new Painel(geral.semPartes(), Map.of());
        }
    }

    public static Painel painel(List<JuditLeitura.Processo> lista, LocalDate hoje) {
        Map<String, Bloco> porJustica = new LinkedHashMap<>();
        for (String justica : JUSTICAS) {
            porJustica.put(justica, bloco(filtrar(lista, p -> justica.equals(p.justica())), hoje));
        }
        for (String extra : List.of("SUPERIOR", "OUTRA")) {
            List<JuditLeitura.Processo> deles = filtrar(lista, p -> extra.equals(p.justica()));
            if (!deles.isEmpty()) {
                porJustica.put(extra, bloco(deles, hoje));
            }
        }
        return new Painel(bloco(lista, hoje), porJustica);
    }

    static Bloco bloco(List<JuditLeitura.Processo> lista, LocalDate hoje) {
        Map<String, Soma> porJustica = new LinkedHashMap<>();
        JUSTICAS.forEach(j -> porJustica.put(j, soma(filtrar(lista, p -> j.equals(p.justica())))));

        Map<String, PorPolo> porPolo = new LinkedHashMap<>();
        for (String polo : List.of(REU, AUTOR, OUTROS)) {
            porPolo.put(polo, porPolo(filtrar(lista, p -> polo.equals(grupo(p)))));
        }

        return new Bloco(
                soma(lista),
                porPolo.get(REU).soma(),
                porPolo.get(AUTOR).soma(),
                porPolo.get(OUTROS).soma(),
                porJustica,
                areas(lista),
                contar(lista, p -> List.of(p.uf() == null ? "" : p.uf()), false),
                porAno(lista),
                ultimos12Meses(lista, hoje),
                porPolo,
                soma(filtrar(lista, JuditLeitura.Processo::segmentoFinanceiro)),
                partes(lista),
                ultimos(lista));
    }

    /** O polo da empresa, com terceiro e desconhecido juntos em "outros", como no Vadu. */
    static String grupo(JuditLeitura.Processo p) {
        return REU.equals(p.polo()) || AUTOR.equals(p.polo()) ? p.polo() : OUTROS;
    }

    private static List<JuditLeitura.Processo> filtrar(List<JuditLeitura.Processo> lista,
                                                       Predicate<JuditLeitura.Processo> criterio) {
        return lista.stream().filter(criterio).toList();
    }

    private static Soma soma(List<JuditLeitura.Processo> lista) {
        if (lista.isEmpty()) {
            return Soma.ZERO;
        }
        return new Soma(lista.size(), lista.stream().map(JuditLeitura.Processo::valor).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static List<Area> areas(List<JuditLeitura.Processo> lista) {
        Map<String, List<JuditLeitura.Processo>> porArea = new LinkedHashMap<>();
        lista.forEach(p -> porArea.computeIfAbsent(p.area() == null ? "" : p.area(), k -> new ArrayList<>()).add(p));
        return porArea.entrySet().stream()
                .map(e -> new Area(
                        e.getKey().isEmpty() ? null : e.getKey(),
                        e.getValue().size(),
                        lista.isEmpty() ? 0 : 100.0 * e.getValue().size() / lista.size(),
                        e.getValue().stream().map(JuditLeitura.Processo::distribuidoEm).filter(Objects::nonNull)
                                .min(Comparator.naturalOrder()).orElse(null),
                        e.getValue().stream().map(JuditPainel::ultimaMovimentacao).filter(Objects::nonNull)
                                .max(Comparator.naturalOrder()).orElse(null)))
                .sorted(Comparator.comparingInt(Area::quantidade).reversed())
                .toList();
    }

    private static List<PorPeriodo> porAno(List<JuditLeitura.Processo> lista) {
        return periodos(filtrar(lista, p -> p.distribuidoEm() != null), p -> String.valueOf(p.distribuidoEm().getYear()));
    }

    /** Distribuídos nos últimos 12 meses, mês a mês; só os meses com processo, como no Vadu. */
    private static List<PorPeriodo> ultimos12Meses(List<JuditLeitura.Processo> lista, LocalDate hoje) {
        YearMonth desde = YearMonth.from(hoje).minusMonths(11);
        return periodos(
                filtrar(lista, p -> p.distribuidoEm() != null && !YearMonth.from(p.distribuidoEm()).isBefore(desde)),
                p -> YearMonth.from(p.distribuidoEm()).toString());
    }

    private static List<PorPeriodo> periodos(List<JuditLeitura.Processo> lista,
                                             Function<JuditLeitura.Processo, String> periodo) {
        Map<String, int[]> contagem = new TreeMap<>();
        for (JuditLeitura.Processo p : lista) {
            int[] c = contagem.computeIfAbsent(periodo.apply(p), k -> new int[3]);
            c[switch (grupo(p)) {
                case REU -> 0;
                case AUTOR -> 1;
                default -> 2;
            }]++;
        }
        return contagem.entrySet().stream()
                .map(e -> new PorPeriodo(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private static PorPolo porPolo(List<JuditLeitura.Processo> lista) {
        Map<String, List<JuditLeitura.Processo>> porSituacao = new LinkedHashMap<>();
        lista.forEach(p -> porSituacao.computeIfAbsent(p.situacao() == null ? "" : p.situacao(), k -> new ArrayList<>()).add(p));
        List<PorSituacao> situacoes = porSituacao.entrySet().stream()
                .map(e -> {
                    Soma s = soma(e.getValue());
                    return new PorSituacao(e.getKey().isEmpty() ? null : e.getKey(), s.quantidade(), s.valor());
                })
                .sorted(Comparator.comparing(PorSituacao::valor).reversed().thenComparing(PorSituacao::quantidade, Comparator.reverseOrder()))
                .toList();
        return new PorPolo(soma(lista), situacoes,
                contar(lista, p -> p.classe() == null ? List.of() : List.of(p.classe()), true),
                contar(lista, JuditLeitura.Processo::assuntos, true));
    }

    /** Quantos processos têm cada valor. Um processo conta uma vez por valor, mesmo repetido. */
    private static List<JuditLeitura.Contagem> contar(List<JuditLeitura.Processo> lista,
                                                      Function<JuditLeitura.Processo, List<String>> valores,
                                                      boolean semVazio) {
        Map<String, Integer> contagem = new LinkedHashMap<>();
        for (JuditLeitura.Processo p : lista) {
            valores.apply(p).stream().distinct()
                    .filter(v -> !semVazio || (v != null && !v.isBlank()))
                    .forEach(v -> contagem.merge(v, 1, Integer::sum));
        }
        return contagem.entrySet().stream()
                .map(e -> new JuditLeitura.Contagem(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(JuditLeitura.Contagem::quantidade).reversed())
                .toList();
    }

    private static List<Parte> partes(List<JuditLeitura.Processo> lista) {
        Map<String, Object[]> porNome = new LinkedHashMap<>();
        for (JuditLeitura.Processo p : lista) {
            for (String nome : p.contrapartes().stream().distinct().toList()) {
                Object[] c = porNome.computeIfAbsent(nome, k -> new Object[] {0, 0, 0, BigDecimal.ZERO});
                c[0] = (int) c[0] + 1;
                if (REU.equals(p.polo())) {
                    c[1] = (int) c[1] + 1;
                } else if (AUTOR.equals(p.polo())) {
                    c[2] = (int) c[2] + 1;
                }
                if (p.valor() != null) {
                    c[3] = ((BigDecimal) c[3]).add(p.valor());
                }
            }
        }
        return porNome.entrySet().stream()
                .map(e -> new Parte(e.getKey(), (int) e.getValue()[0], (int) e.getValue()[1], (int) e.getValue()[2],
                        (BigDecimal) e.getValue()[3]))
                .sorted(Comparator.comparingInt(Parte::quantidade).reversed().thenComparing(Parte::valor, Comparator.reverseOrder()))
                .toList();
    }

    private static List<Ultimo> ultimos(List<JuditLeitura.Processo> lista) {
        return lista.stream()
                .sorted(Comparator.comparing(JuditPainel::ultimaMovimentacao, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(ULTIMOS)
                .map(p -> new Ultimo(p.cnj(), p.tribunal(), p.uf(), p.classe(), p.polo(), p.situacao(), p.distribuidoEm(),
                        ultimaMovimentacao(p)))
                .toList();
    }

    private static LocalDate ultimaMovimentacao(JuditLeitura.Processo p) {
        return p.ultimoAndamento() != null && p.ultimoAndamento().data() != null ? p.ultimoAndamento().data() : p.distribuidoEm();
    }
}
