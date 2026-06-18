package com.portal.serasa.domain.model.credit;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO que contém todos os dados extraídos do CreditAnalysis (via JSONB)
 * necessários para a análise de crédito do Motor.
 */
@Data
@Builder
public class DadosAnalise {

    // ========== SCORES SERASA ==========

    private Integer scoreHPJ8;
    private Integer scoreHIP2;
    private Integer scoreHSJ6;
    private Integer scoreHFE3;
    private Integer scoreHLO4;

    // ========== DADOS NEGATIVOS ==========

    private Integer totalPendencias;
    private BigDecimal totalValorDividas;
    private Integer pefinCount;
    private BigDecimal pefinBalance;
    private Integer refinCount;
    private BigDecimal refinBalance;
    private Integer protestoCount;
    private BigDecimal protestoBalance;
    private Integer acaoJudicialCount;
    private BigDecimal acaoJudicialBalance;
    private Integer chequeCount;
    private BigDecimal chequeBalance;

    // ========== CLASSIFICAÇÃO DE RISCO ==========

    private String classeRisco;
    private Double probabilidadeInadimplencia;
    private Double probabilidadeMediaInadimplencia;

    // ========== LONGEVIDADE ==========

    private String perspectiva;
    private Double probabilidadeEncerramento;
    private String mensagemLongevidade;

    // ========== DADOS DA EMPRESA ==========

    private LocalDate dataFundacao;
    private Integer idadeMeses;
    private String porte;
    private String porteCodigo;

    // ========== SÓCIOS ==========

    private boolean possuiSocioComRestricao;
    private int quantidadeSocios;
    private String socioComRestricaoNome;

    // ========== CONSULTAS RECEBIDAS ==========

    private int consultasUltimos30Dias;
    private int consultasUltimos90Dias;
    private int consultasTotal;

    // ========== MÉTODOS AUXILIARES ==========

    public boolean temNegativosAtivos() {
        return totalPendencias != null && totalPendencias > 0;
    }

    public boolean temDividasRelevantes(double limite) {
        return totalValorDividas != null && totalValorDividas.doubleValue() > limite;
    }

    public boolean isEmpresaNova(int mesesLimite) {
        return idadeMeses != null && idadeMeses < mesesLimite;
    }

    public boolean temScoreCritico() {
        return scoreHPJ8 != null && scoreHPJ8 < 200;
    }

    public boolean temPontualidadeRuim(int limite) {
        return scoreHIP2 != null && scoreHIP2 < limite;
    }

    public boolean temRiscoJuridico(int limite) {
        return scoreHSJ6 != null && scoreHSJ6 < limite;
    }

    public double getTotalDividasDouble() {
        return totalValorDividas != null ? totalValorDividas.doubleValue() : 0.0;
    }

    public boolean isGrandePorte() {
        return scoreHFE3 != null && scoreHFE3 > 80;
    }
}
