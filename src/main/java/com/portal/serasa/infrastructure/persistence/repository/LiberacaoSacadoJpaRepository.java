package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoSacadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoSacadoJpaRepository extends JpaRepository<LiberacaoSacadoEntity, UUID> {

    List<LiberacaoSacadoEntity> findByCardIdOrderByOrdem(UUID cardId);

    List<LiberacaoSacadoEntity> findByCardIdIn(Collection<UUID> cardIds);

    /** Delete em lote antes de regravar: o índice único (card, cnpj) não pode ver as duas versões. */
    @Modifying(flushAutomatically = true)
    @Query("delete from LiberacaoSacadoEntity s where s.cardId = :cardId")
    void apagarDoCard(@Param("cardId") UUID cardId);
}
