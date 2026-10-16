package com.portal.serasa.domain.exception;

/** A consulta passaria do limite mensal de gasto com a Judit. Vira 409. */
public class LimiteJuditException extends DomainException {
    public LimiteJuditException(String message) {
        super(message);
    }
}
