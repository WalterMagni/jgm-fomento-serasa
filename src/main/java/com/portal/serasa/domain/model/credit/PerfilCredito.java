package com.portal.serasa.domain.model.credit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum que representa os 10 perfis de risco de crédito.
 * Cada perfil possui uma decisão, cor e mensagem padrão associada.
 * 
 * A prioridade indica a ordem de verificação no funil de decisão
 * (números menores = verificados primeiro = mais críticos).
 */
@Getter
@RequiredArgsConstructor
public enum PerfilCredito {

    // ========== CRÍTICOS (Knockout) ==========

    /**
     * REJEICAO - Score crítico ou dívidas altas
     * Regra: HPJ8 < 200 OU Dívidas > R$ 2.000
     */
    REJEICAO(1, "REPROVADO", "Alto Risco / Inadimplente", "#C0392B",
            "Empresa possui restrições financeiras ativas ou score de crédito crítico. Operação não recomendada."),

    /**
     * RISCO_SOCIO - Sócio com CPF negativado
     * Regra: Sócio com possuiRestricao = true
     */
    RISCO_SOCIO(2, "EXIGIR GARANTIA", "Risco de Contágio / Sócios", "#922B21",
            "Empresa operacionalmente ativa, porém quadro societário apresenta restrições em CPF. Risco de confusão patrimonial."),

    // ========== ALERTAS ESPECIAIS ==========

    /**
     * RISCO_JURIDICO - Score bom mas processos ocultos
     * Regra: HPJ8 > 600 E HSJ6 < 450
     */
    RISCO_JURIDICO(3, "REVISÃO MANUAL", "Risco Jurídico Latente", "#8E44AD",
            "Dissonância Financeira-Jurídica. A empresa paga bancos em dia, mas enfrenta alto volume judicial."),

    /**
     * FOME_CREDITO - Muitas consultas recentes
     * Regra: Consultas > 5 (30 dias) E HPJ8 < 700
     */
    FOME_CREDITO(4, "VENDA À VISTA", "Alta Demanda de Crédito", "#E67E22",
            "Empresa buscou crédito excessivamente nos últimos 30 dias. Indica dificuldade de caixa momentânea."),

    // ========== ATENÇÃO ==========

    /**
     * RECUPERACAO - Score baixo ou negativos pequenos
     * Regra: HPJ8 < 400 OU tem negativos ativos
     */
    RECUPERACAO(5, "ANÁLISE MANUAL", "Perfil de Risco Elevado", "#D35400",
            "Empresa com histórico recente de dificuldades ou score baixo. Sugere-se venda à vista ou com garantia."),

    /**
     * ALERTA_OPERACIONAL - Tem dinheiro mas paga mal
     * Regra: HPJ8 > 500 E HIP2 < 50
     */
    ALERTA_OPERACIONAL(6, "APROVADO COM RESTRIÇÃO", "Risco Operacional", "#F1C40F",
            "Empresa solvente, porém com hábito de pagamento ruim. Alto risco de atraso, baixo risco de calote final."),

    /**
     * STARTUP - Empresa nova (< 2 anos)
     * Regra: Idade < 24 meses E sem negativos
     */
    STARTUP(7, "LIMITE INICIAL", "Nova Empresa (Early Stage)", "#3498DB",
            "Empresa constituída recentemente. Ausência de histórico negativo, porém maturidade insuficiente."),

    // ========== APROVADOS ==========

    /**
     * GIGANTE_BUROCRATICO - Grande com dívida pequena (falso positivo)
     * Regra: HFE3 > 80 E Dívidas < R$ 2.000 E Dívidas > 0
     */
    GIGANTE_BUROCRATICO(8, "APROVAR", "Corporate / Risco Operacional Baixo", "#27AE60",
            "Empresa de grande porte com apontamentos de baixo valor (imateriais frente ao faturamento)."),

    /**
     * PRIME - Cliente perfeito
     * Regra: HPJ8 > 750 E HSJ6 > 800
     */
    PRIME(9, "APROVAÇÃO AUTOMÁTICA", "Cliente Prime", "#1D8348",
            "Excelente pagador. Histórico jurídico limpo e alta solidez financeira. Limite máximo sugerido."),

    /**
     * STANDARD - PME saudável (fallback - maioria das empresas)
     * Regra: Não caiu em nenhum outro perfil
     */
    STANDARD(10, "APROVADO", "Standard / PME", "#2ECC71",
            "Empresa apresenta estabilidade e pontualidade adequada. Risco de crédito moderado compatível com o porte.");

    private final int prioridade;
    private final String decisao;
    private final String titulo;
    private final String corHex;
    private final String mensagemPadrao;

    /**
     * Verifica se o perfil resulta em aprovação automática
     */
    public boolean isAprovado() {
        return this == PRIME || this == STANDARD || this == GIGANTE_BUROCRATICO;
    }

    /**
     * Verifica se o perfil resulta em aprovação com restrições
     */
    public boolean isAprovadoComRestricao() {
        return this == ALERTA_OPERACIONAL || this == STARTUP;
    }

    /**
     * Verifica se o perfil resulta em reprovação
     */
    public boolean isReprovado() {
        return this == REJEICAO;
    }

    /**
     * Verifica se o perfil exige análise manual
     */
    public boolean exigeAnaliseManual() {
        return this == RECUPERACAO || this == RISCO_JURIDICO || this == RISCO_SOCIO || this == FOME_CREDITO;
    }

    /**
     * Retorna a cor RGB
     */
    public int[] getCorRGB() {
        String hex = corHex.replace("#", "");
        return new int[] {
                Integer.parseInt(hex.substring(0, 2), 16),
                Integer.parseInt(hex.substring(2, 4), 16),
                Integer.parseInt(hex.substring(4, 6), 16)
        };
    }
}
