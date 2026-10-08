package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoAnexoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoAnexoJpaRepository extends JpaRepository<LiberacaoAnexoEntity, UUID> {

    List<LiberacaoAnexoEntity> findByCardIdAndRemovidoEmIsNullOrderByEnviadoEmDesc(UUID cardId);

    /** Conta inclusive os removidos: o byte antigo continua no disco e o nome não pode repetir. */
    long countByCardId(UUID cardId);

    @Query("""
            select a.cardId, count(a) from LiberacaoAnexoEntity a
            where a.cardId in :cardIds and a.removidoEm is null
            group by a.cardId
            """)
    List<Object[]> contarPorCard(@Param("cardIds") Collection<UUID> cardIds);
}
