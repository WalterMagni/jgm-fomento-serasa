package com.portal.serasa.domain.exception;

/**
 * Alguém salvou o card depois que você o abriu. Vira 409 com o nome de quem salvou, para a tela
 * pedir recarga em vez de sobrescrever o trabalho da outra pessoa.
 */
public class ConflitoEdicaoException extends DomainException {

    public ConflitoEdicaoException(String message) {
        super(message);
    }
}
