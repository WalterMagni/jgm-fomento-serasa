package com.portal.serasa.domain.exception;

/**
 * Transição que a máquina de estados da esteira não permite, ou disputa por um card que outra
 * pessoa já assumiu. Vira 409.
 */
public class TransicaoInvalidaException extends DomainException {

    public TransicaoInvalidaException(String message) {
        super(message);
    }
}
