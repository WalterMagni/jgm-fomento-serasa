package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDecisaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProspeccaoDecisaoJpaRepository extends JpaRepository<ProspeccaoDecisaoEntity, UUID> {

    List<ProspeccaoDecisaoEntity> findByProspeccaoIdOrderByVersaoDesc(UUID prospeccaoId);

    Optional<ProspeccaoDecisaoEntity> findByProspeccaoIdAndVigenteTrue(UUID prospeccaoId);

    /** Decisões vigentes de vários cards, numa consulta só — o quadro mostra o limite de cada um. */
    List<ProspeccaoDecisaoEntity> findByProspeccaoIdInAndVigenteTrue(Collection<UUID> prospeccaoIds);

    @Query("select coalesce(max(d.versao), 0) from ProspeccaoDecisaoEntity d where d.prospeccaoId = :id")
    int ultimaVersao(@Param("id") UUID prospeccaoId);

    /**
     * Tira a vigência de todas as decisões do card. Roda antes de gravar a nova, e o índice único
     * parcial da V75 garante que nunca existam duas vigentes ao mesmo tempo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProspeccaoDecisaoEntity d set d.vigente = false where d.prospeccaoId = :id and d.vigente = true")
    int encerrarVigencia(@Param("id") UUID prospeccaoId);
}
