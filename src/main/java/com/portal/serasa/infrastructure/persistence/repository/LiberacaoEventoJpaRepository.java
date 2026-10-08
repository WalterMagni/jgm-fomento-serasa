package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoEventoJpaRepository extends JpaRepository<LiberacaoEventoEntity, UUID> {

    List<LiberacaoEventoEntity> findByCardIdOrderByCriadoEmDesc(UUID cardId);

    List<LiberacaoEventoEntity> findByCardIdInOrderByCriadoEm(Collection<UUID> cardIds);

    /** Último evento de cada card: base da ordenação por "última atividade". */
    @Query("select e.cardId, max(e.criadoEm) from LiberacaoEventoEntity e where e.cardId in :cardIds group by e.cardId")
    List<Object[]> ultimoPorCard(@Param("cardIds") Collection<UUID> cardIds);
}
