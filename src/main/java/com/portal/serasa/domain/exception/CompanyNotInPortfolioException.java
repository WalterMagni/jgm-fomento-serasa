package com.portal.serasa.domain.exception;

/**
 * Sinaliza que o CNPJ informado não está na carteira (sem cadastro em
 * company_details) e o chamador não autorizou criá-lo. O frontend usa isso
 * para perguntar se quer criar a empresa com os dados do CNPJ Já.
 */
public class CompanyNotInPortfolioException extends RuntimeException {

    private final String cnpj;

    public CompanyNotInPortfolioException(String cnpj) {
        super("CNPJ " + cnpj + " não está na carteira");
        this.cnpj = cnpj;
    }

    public String getCnpj() {
        return cnpj;
    }
}
