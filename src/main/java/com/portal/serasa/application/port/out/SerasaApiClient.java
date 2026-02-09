package com.portal.serasa.application.port.out;

import com.portal.serasa.domain.model.CreditAnalysis;

/**
 * Port para integração com a API Serasa.
 * Define o contrato de consulta de crédito externa.
 */
public interface SerasaApiClient {

    /**
     * Consulta dados de crédito na API Serasa pelo CNPJ.
     *
     * @param cnpj CNPJ da empresa (14 dígitos)
     * @return dados resumidos da análise de crédito retornados pela Serasa
     */
    CreditAnalysis consultarCredito(String cnpj);
}
