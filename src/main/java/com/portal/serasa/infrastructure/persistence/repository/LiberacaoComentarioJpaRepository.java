package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LiberacaoComentarioJpaRepository extends JpaRepository<LiberacaoComentarioEntity, UUID> {

    List<LiberacaoComentarioEntity> findByCardIdAndExcluidoEmIsNullOrderByCriadoEmDesc(UUID cardId);

    /** Contagem por card para a face do quadro, numa consulta só. */
    @Query("""
            select c.cardId, count(c) from LiberacaoComentarioEntity c
            where c.cardId in :cardIds and c.excluidoEm is null
            group by c.cardId
            """)
    List<Object[]> contarPorCard(@Param("cardIds") Collection<UUID> cardIds);
}
