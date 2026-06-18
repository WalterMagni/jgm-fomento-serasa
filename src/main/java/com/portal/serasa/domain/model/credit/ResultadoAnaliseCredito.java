package com.portal.serasa.domain.model.credit;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO que representa o resultado completo da análise de crédito.
 * Contém o perfil identificado, decisão, scores e insights.
 */
@Data
@Builder
public class ResultadoAnaliseCredito {

    // ========== IDENTIFICAÇÃO ==========
    private String cnpj;
    private String razaoSocial;
    private String nomeFantasia;
    private LocalDateTime dataAnalise;

    // ========== RESULTADO PRINCIPAL ==========
    private PerfilCredito perfil;
    private String decisao;
    private String titulo;
    private String corHex;
    private String mensagem;

    // ========== SCORES UTILIZADOS ==========
    private Integer scoreCredito;
    private Integer scorePontualidade;
    private Integer scoreJuridico;
    private Integer scoreFaturamento;
    private Integer scoreLongevidade;

    // ========== INSIGHTS ==========
    @Builder.Default
    private List<String> alertas = new ArrayList<>();

    @Builder.Default
    private List<String> pontosPositivos = new ArrayList<>();

    @Builder.Default
    private List<String> recomendacoes = new ArrayList<>();

    // ========== LIMITES SUGERIDOS ==========
    private String limiteCredito;
    private String prazoPagamento;

    // ========== MÉTRICAS RESUMIDAS ==========
    private Double probabilidadeInadimplencia;
    private Double probabilidadeEncerramento;
    private Integer idadeEmpresaMeses;
    private Integer totalPendenciasFinanceiras;
    private Double totalValorDividas;
    private Integer consultasRecentes;

    // ========== MÉTODOS AUXILIARES ==========

    public void addAlerta(String alerta) {
        if (alertas == null)
            alertas = new ArrayList<>();
        alertas.add(alerta);
    }

    public void addPontoPositivo(String ponto) {
        if (pontosPositivos == null)
            pontosPositivos = new ArrayList<>();
        pontosPositivos.add(ponto);
    }

    public void addRecomendacao(String recomendacao) {
        if (recomendacoes == null)
            recomendacoes = new ArrayList<>();
        recomendacoes.add(recomendacao);
    }

    public boolean isAprovado() {
        return perfil != null && (perfil.isAprovado() || perfil.isAprovadoComRestricao());
    }

    public boolean isReprovado() {
        return perfil != null && perfil.isReprovado();
    }

    public boolean precisaAnaliseManual() {
        return perfil != null && perfil.exigeAnaliseManual();
    }

    public String getResumoOneLine() {
        return String.format("[%s] %s - %s (Score: %d)",
                decisao,
                cnpj,
                titulo,
                scoreCredito != null ? scoreCredito : 0);
    }
}
