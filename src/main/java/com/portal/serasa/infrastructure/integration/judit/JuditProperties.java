package com.portal.serasa.infrastructure.integration.judit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Chave, endereço e preços da Judit ({@code judit.*} no application.yml).
 *
 * <p>Os preços ficam aqui, e não no banco, porque são os do contrato: mudam quando o contrato muda,
 * junto com um deploy. O que a Judit cobrou de fato só aparece na fatura — a tabela de consultas
 * guarda a estimativa.</p>
 */
@ConfigurationProperties(prefix = "judit")
public record JuditProperties(
        String apiKey,
        String lawsuitsUrl,
        String requestsUrl,
        BigDecimal custoResumo,
        BigDecimal custoProcessosPorMil,
        BigDecimal custoDetalhe,
        BigDecimal avisoMensal,
        BigDecimal limiteMensal,
        int validadeDias,
        int acompanhamentoIntervaloSegundos,
        int acompanhamentoLimiteMinutos) {

    public boolean configurada() {
        return apiKey != null && !apiKey.isBlank();
    }
}
