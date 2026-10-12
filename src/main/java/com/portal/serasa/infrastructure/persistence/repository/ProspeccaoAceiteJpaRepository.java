package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoAceiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProspeccaoAceiteJpaRepository extends JpaRepository<ProspeccaoAceiteEntity, UUID> {

    List<ProspeccaoAceiteEntity> findByProspeccaoIdOrderByRegistradoEmDesc(UUID prospeccaoId);

    List<ProspeccaoAceiteEntity> findByProspeccaoIdInOrderByRegistradoEmDesc(java.util.Collection<UUID> prospeccaoIds);
}
