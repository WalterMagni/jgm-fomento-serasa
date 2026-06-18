package com.portal.serasa.infrastructure.integration.serasa;

/**
 * Cliente para consulta do relatório Credit Rating na API Serasa.
 */
public interface SerasaCreditRatingClient {

    /**
     * Consulta o relatório Credit Rating pelo CNPJ.
     *
     * @param cnpj CNPJ da empresa (14 dígitos)
     * @return JSON bruto da resposta da API
     */
    String consultarCreditRating(String cnpj);
}
