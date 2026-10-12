package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoCardEtiquetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProspeccaoCardEtiquetaJpaRepository
        extends JpaRepository<ProspeccaoCardEtiquetaEntity, ProspeccaoCardEtiquetaEntity.Chave> {

    List<ProspeccaoCardEtiquetaEntity> findByProspeccaoId(UUID prospeccaoId);

    List<ProspeccaoCardEtiquetaEntity> findByProspeccaoIdIn(Collection<UUID> prospeccaoIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ProspeccaoCardEtiquetaEntity e where e.prospeccaoId = :id")
    void apagarDoCard(@Param("id") UUID prospeccaoId);
}
