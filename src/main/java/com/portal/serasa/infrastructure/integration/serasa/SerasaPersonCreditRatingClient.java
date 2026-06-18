package com.portal.serasa.infrastructure.integration.serasa;

public interface SerasaPersonCreditRatingClient {

    /**
     * Consulta o relatório RELATORIO_AVANCADO_PF para o CPF informado.
     *
     * @param cpf CPF com 11 dígitos numéricos
     * @return JSON bruto retornado pela Serasa
     */
    String consultarCreditRatingPF(String cpf);
}
