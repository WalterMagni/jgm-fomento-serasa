package com.portal.serasa.application.service.judit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** O painel no desenho do Vadu, montado da lista da Judit; e a justiça e a situação de cada processo. */
class JuditPainelTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 26);

    private static JuditLeitura.Processo processo(String cnj, String polo, String area, String classe, List<String> assuntos,
                                                  String valor, LocalDate distribuido, LocalDate ultimo, String justica,
                                                  String situacao, String uf, List<String> contrapartes, boolean financeiro) {
        return new JuditLeitura.Processo(cnj, List.of(1), null, polo, contrapartes, "TJ" + uf, List.of(), uf, null, area,
                classe, assuntos, valor == null ? null : new BigDecimal(valor), distribuido,
                ultimo == null ? null : new JuditLeitura.Andamento(ultimo, "x"), false, null, justica, situacao, financeiro);
    }

    private static final List<JuditLeitura.Processo> LISTA = List.of(
            processo("A", JuditLeitura.REU, "DIREITO CIVIL", "EXECUÇÃO DE TÍTULO EXTRAJUDICIAL", List.of("DUPLICATA"),
                    "1000", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 10, 20), "ESTADUAL", "EM_TRAMITACAO", "SP",
                    List.of("BANCO X"), true),
            processo("B", JuditLeitura.REU, "DIREITO CIVIL", "EXECUÇÃO DE TÍTULO EXTRAJUDICIAL", List.of("DUPLICATA", "CHEQUE"),
                    "500", LocalDate.of(2020, 1, 10), LocalDate.of(2022, 5, 1), "ESTADUAL", "ARQUIVADO", "PR",
                    List.of("BANCO X"), true),
            processo("C", JuditLeitura.AUTOR, null, "AGRAVO DE INSTRUMENTO", List.of(),
                    null, LocalDate.of(2018, 3, 1), null, "ESTADUAL", null, "SP", List.of("FULANO LTDA"), false),
            processo("D", JuditLeitura.TERCEIRO, "DIREITO DO TRABALHO", "RECLAMAÇÃO TRABALHISTA", List.of(),
                    "200", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), "TRABALHO", "EM_TRAMITACAO", "SP",
                    List.of(), false));

    @Test
    @DisplayName("cards: total e por justiça com quantidade e valor; polos com terceiro em outros")
    void cards() {
        JuditPainel.Bloco geral = JuditPainel.painel(LISTA, HOJE).geral();

        assertThat(geral.total()).isEqualTo(new JuditPainel.Soma(4, new BigDecimal("1700")));
        assertThat(geral.porJustica().get("ESTADUAL")).isEqualTo(new JuditPainel.Soma(3, new BigDecimal("1500")));
        assertThat(geral.porJustica().get("TRABALHO").quantidade()).isEqualTo(1);
        assertThat(geral.porJustica().get("FEDERAL")).isEqualTo(new JuditPainel.Soma(0, BigDecimal.ZERO));
        assertThat(geral.comoReu().quantidade()).isEqualTo(2);
        assertThat(geral.comoAutor().quantidade()).isEqualTo(1);
        assertThat(geral.outros().quantidade()).isEqualTo(1);
        assertThat(geral.segmentoFinanceiro()).isEqualTo(new JuditPainel.Soma(2, new BigDecimal("1500")));
    }

    @Test
    @DisplayName("áreas: quantidade, %, primeiro processo e última movimentação; área vazia vira nula")
    void areas() {
        List<JuditPainel.Area> areas = JuditPainel.painel(LISTA, HOJE).geral().areas();

        assertThat(areas.get(0).area()).isEqualTo("DIREITO CIVIL");
        assertThat(areas.get(0).quantidade()).isEqualTo(2);
        assertThat(areas.get(0).porcentagem()).isEqualTo(50.0);
        assertThat(areas.get(0).primeiroProcesso()).isEqualTo(LocalDate.of(2020, 1, 10));
        assertThat(areas.get(0).ultimaMovimentacao()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(areas).extracting(JuditPainel.Area::area).contains((String) null);
    }

    @Test
    @DisplayName("por ano e últimos 12 meses, separados por polo")
    void periodos() {
        JuditPainel.Bloco geral = JuditPainel.painel(LISTA, HOJE).geral();

        assertThat(geral.porAno()).containsExactly(
                new JuditPainel.PorPeriodo("2018", 0, 1, 0),
                new JuditPainel.PorPeriodo("2020", 1, 0, 0),
                new JuditPainel.PorPeriodo("2026", 1, 0, 1));
        assertThat(geral.ultimos12Meses()).containsExactly(
                new JuditPainel.PorPeriodo("2026-07", 0, 0, 1),
                new JuditPainel.PorPeriodo("2026-09", 1, 0, 0));
    }

    @Test
    @DisplayName("por polo: situações somadas por valor, classes e assuntos contados por processo")
    void porPolo() {
        JuditPainel.PorPolo reu = JuditPainel.painel(LISTA, HOJE).geral().porPolo().get(JuditPainel.REU);

        assertThat(reu.situacoes()).containsExactly(
                new JuditPainel.PorSituacao("EM_TRAMITACAO", 1, new BigDecimal("1000")),
                new JuditPainel.PorSituacao("ARQUIVADO", 1, new BigDecimal("500")));
        assertThat(reu.classes()).containsExactly(new JuditLeitura.Contagem("EXECUÇÃO DE TÍTULO EXTRAJUDICIAL", 2));
        assertThat(reu.assuntos().get(0)).isEqualTo(new JuditLeitura.Contagem("DUPLICATA", 2));
    }

    @Test
    @DisplayName("partes, últimos processos e as justiças: as cinco sempre, separadas")
    void partesEJusticas() {
        JuditPainel.Painel painel = JuditPainel.painel(LISTA, HOJE);

        assertThat(painel.geral().partes().get(0))
                .isEqualTo(new JuditPainel.Parte("BANCO X", 2, 2, 0, new BigDecimal("1500")));
        assertThat(painel.geral().ultimos()).extracting(JuditPainel.Ultimo::cnj).containsExactly("A", "D", "B", "C");
        assertThat(painel.porJustica()).containsOnlyKeys("ESTADUAL", "TRABALHO", "FEDERAL", "ELEITORAL", "MILITAR");
        assertThat(painel.porJustica().get("TRABALHO").total().quantidade()).isEqualTo(1);
        assertThat(painel.soGeral().porJustica()).isEmpty();
        assertThat(painel.soGeral().geral().partes()).isEmpty();
    }

    @Test
    @DisplayName("justiça pelo dígito do número CNJ, ou pela descrição da Judit")
    void justica() {
        assertThat(JuditLeitura.justica("4018969-75.2026.8.26.0004", null)).isEqualTo("ESTADUAL");
        assertThat(JuditLeitura.justica("0000123-45.2024.5.09.0001", null)).isEqualTo("TRABALHO");
        assertThat(JuditLeitura.justica("5001234-56.2023.4.04.7000", null)).isEqualTo("FEDERAL");
        assertThat(JuditLeitura.justica("0000001-00.2024.3.00.0000", null)).isEqualTo("SUPERIOR");
        assertThat(JuditLeitura.justica(null, "JUSTIÇA DO TRABALHO")).isEqualTo("TRABALHO");
        assertThat(JuditLeitura.justica("sem padrão", null)).isEqualTo("OUTRA");
    }

    @Test
    @DisplayName("situação deduzida do último andamento: arquivado, suspenso, desarquivado, em tramitação")
    void situacao() {
        assertThat(JuditLeitura.situacao("NÃO INFORMADO", "Arquivado Definitivamente")).isEqualTo("ARQUIVADO");
        assertThat(JuditLeitura.situacao(null, "Baixa Definitiva")).isEqualTo("ARQUIVADO");
        assertThat(JuditLeitura.situacao(null, "Extinta a execução ou o cumprimento da sentença")).isEqualTo("ARQUIVADO");
        assertThat(JuditLeitura.situacao(null, "Certidão de Trânsito em Julgado")).isEqualTo("ARQUIVADO");
        assertThat(JuditLeitura.situacao(null, "Arquivamento provisório")).isEqualTo("SUSPENSO");
        assertThat(JuditLeitura.situacao(null, "Processo suspenso por execução frustrada")).isEqualTo("SUSPENSO");
        assertThat(JuditLeitura.situacao(null, "Desarquivamento de autos")).isEqualTo("EM_TRAMITACAO");
        assertThat(JuditLeitura.situacao(null, "Juntada de Petição de requerimento")).isEqualTo("EM_TRAMITACAO");
        assertThat(JuditLeitura.situacao("ARQUIVADO", "Juntada de petição")).isEqualTo("ARQUIVADO");
        assertThat(JuditLeitura.situacao("NÃO INFORMADO", null)).isNull();
    }
}
