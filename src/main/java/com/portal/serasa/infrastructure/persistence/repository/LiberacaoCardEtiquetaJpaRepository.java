package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEtiquetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoCardEtiquetaJpaRepository
        extends JpaRepository<LiberacaoCardEtiquetaEntity, LiberacaoCardEtiquetaEntity.Chave> {

    List<LiberacaoCardEtiquetaEntity> findByCardId(UUID cardId);

    List<LiberacaoCardEtiquetaEntity> findByCardIdIn(Collection<UUID> cardIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from LiberacaoCardEtiquetaEntity v where v.cardId = :cardId")
    void apagarDoCard(@Param("cardId") UUID cardId);
}
