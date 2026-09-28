package com.portal.serasa.domain.exception;

/** Papel autenticado, mas sem permissão para a operação pedida. Vira 403. */
public class AcessoNegadoException extends DomainException {

    public AcessoNegadoException(String message) {
        super(message);
    }
}
