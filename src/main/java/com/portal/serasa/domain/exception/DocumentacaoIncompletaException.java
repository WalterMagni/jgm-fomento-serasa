package com.portal.serasa.domain.exception;

import java.util.List;

/**
 * Tentativa de fechar a documentação com obrigatório em aberto. Vira 422 com a lista do que
 * falta — a mensagem é o que a tela mostra quando o card volta para a coluna de origem.
 */
public class DocumentacaoIncompletaException extends DomainException {

    private final List<String> pendentes;

    public DocumentacaoIncompletaException(List<String> pendentes) {
        super("Faltam %d documento(s) obrigatório(s): %s"
                .formatted(pendentes.size(), String.join(", ", pendentes)));
        this.pendentes = List.copyOf(pendentes);
    }

    public List<String> getPendentes() {
        return pendentes;
    }
}
