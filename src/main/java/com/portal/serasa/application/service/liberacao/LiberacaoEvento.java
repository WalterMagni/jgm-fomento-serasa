package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;

import java.util.Set;
import java.util.UUID;

/**
 * O que aconteceu num card. Publicado dentro da transação da ação e ouvido depois do commit por
 * {@link LiberacaoNotificador} — notificação de ação que deu rollback não pode sair.
 *
 * <p>{@code mencionados} são só as pessoas que passaram a ser marcadas nesta ação; {@code trecho}
 * é o texto onde foram marcadas, sem marcação, para o resumo da notificação.</p>
 */
public sealed interface LiberacaoEvento {

    LiberacaoCardEntity card();

    UserEntity autor();

    Set<UUID> mencionados();

    String trecho();

    record Criado(LiberacaoCardEntity card, UserEntity autor, Set<UUID> mencionados, String trecho)
            implements LiberacaoEvento {
    }

    record Editado(LiberacaoCardEntity card, UserEntity autor, Set<UUID> mencionados, String trecho)
            implements LiberacaoEvento {
    }

    record Movido(LiberacaoCardEntity card, EtapaLiberacao de, EtapaLiberacao para, UserEntity autor,
                  Set<UUID> mencionados, String trecho) implements LiberacaoEvento {
    }

    record ParecerDado(LiberacaoCardEntity card, LiberacaoParecerEntity parecer, boolean ultimo, boolean revisao,
                       UserEntity autor, Set<UUID> mencionados, String trecho) implements LiberacaoEvento {
    }

    record PendenciaAberta(LiberacaoCardEntity card, LiberacaoPendenciaEntity pendencia, UserEntity autor,
                           Set<UUID> mencionados, String trecho) implements LiberacaoEvento {
    }

    record PendenciaRespondida(LiberacaoCardEntity card, LiberacaoPendenciaEntity pendencia, UserEntity autor,
                               Set<UUID> mencionados, String trecho) implements LiberacaoEvento {
    }

    record Comentado(LiberacaoCardEntity card, LiberacaoComentarioEntity comentario, boolean edicao, UserEntity autor,
                     Set<UUID> mencionados, String trecho) implements LiberacaoEvento {
    }

    record Excluido(LiberacaoCardEntity card, UserEntity autor) implements LiberacaoEvento {
        @Override
        public Set<UUID> mencionados() {
            return Set.of();
        }

        @Override
        public String trecho() {
            return null;
        }
    }
}
