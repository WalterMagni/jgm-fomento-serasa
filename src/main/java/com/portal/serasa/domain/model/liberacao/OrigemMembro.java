package com.portal.serasa.domain.model.liberacao;

/**
 * Por que a pessoa acompanha o card.
 *
 * <p>Comentar ou ser marcado também faz acompanhar, como no GitHub: quem entrou na conversa quer
 * saber da resposta.</p>
 */
public enum OrigemMembro {
    CRIADOR,
    COMITE,
    PENDENCIA,
    COMENTARIO,
    MENCAO,
    MANUAL
}
