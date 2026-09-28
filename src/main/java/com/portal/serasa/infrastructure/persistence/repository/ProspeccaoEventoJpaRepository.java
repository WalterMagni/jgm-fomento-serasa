package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProspeccaoEventoJpaRepository extends JpaRepository<ProspeccaoEventoEntity, UUID> {

    List<ProspeccaoEventoEntity> findByProspeccaoIdOrderByCriadoEmDesc(UUID prospeccaoId);
}
