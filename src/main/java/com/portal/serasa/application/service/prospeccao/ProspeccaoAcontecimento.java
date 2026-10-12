package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;

import java.util.Set;
import java.util.UUID;

/**
 * O que aconteceu num card, publicado na transação da ação e entregue ao
 * {@link ProspeccaoNotificador} depois do commit.
 *
 * <p>Nome diferente de {@code ProspeccaoEventoEntity} de propósito: aquele é a linha da timeline,
 * gravada sempre; este é o aviso para as pessoas, que pode não chegar a ninguém.</p>
 */
public sealed interface ProspeccaoAcontecimento {

    ProspeccaoEntity card();

    UserEntity autor();

    /** Pessoas marcadas com @ no texto. Recebem o aviso de menção no lugar do genérico. */
    default Set<UUID> mencionados() {
        return Set.of();
    }

    /** Trecho do texto para o resumo da notificação. */
    default String trecho() {
        return null;
    }

    record Criado(ProspeccaoEntity card, UserEntity autor) implements ProspeccaoAcontecimento {
    }

    record PendenciaAberta(ProspeccaoEntity card, ProspeccaoPendenciaEntity pendencia, UserEntity autor,
                           Set<UUID> mencionados, String trecho) implements ProspeccaoAcontecimento {
    }

    record PendenciaRespondida(ProspeccaoEntity card, ProspeccaoPendenciaEntity pendencia, UserEntity autor,
                               String trecho) implements ProspeccaoAcontecimento {
    }

    record RespostaCliente(ProspeccaoEntity card, com.portal.serasa.domain.model.prospeccao.RespostaCliente resposta,
                           UserEntity autor, String trecho) implements ProspeccaoAcontecimento {
    }

    /** Comentário novo ou editado. Edição só notifica quem passou a ser mencionado. */
    record Comentado(ProspeccaoEntity card, boolean edicao, UserEntity autor, Set<UUID> mencionados,
                     String trecho) implements ProspeccaoAcontecimento {
    }

    /** Mudou algo que não pede aviso a ninguém, mas o quadro dos outros precisa atualizar. */
    record Alterado(ProspeccaoEntity card, UserEntity autor) implements ProspeccaoAcontecimento {
    }
}
