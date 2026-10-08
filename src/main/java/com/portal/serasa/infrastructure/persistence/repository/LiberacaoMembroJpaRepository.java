package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoMembroJpaRepository
        extends JpaRepository<LiberacaoMembroEntity, LiberacaoMembroEntity.Chave> {

    List<LiberacaoMembroEntity> findByCardIdOrderByAdicionadoEm(UUID cardId);

    List<LiberacaoMembroEntity> findByCardIdIn(Collection<UUID> cardIds);

    boolean existsByCardIdAndUsuarioId(UUID cardId, UUID usuarioId);
}
