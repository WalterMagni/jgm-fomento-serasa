package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProspeccaoDocumentoJpaRepository
        extends JpaRepository<ProspeccaoDocumentoEntity, UUID> {

    List<ProspeccaoDocumentoEntity> findByProspeccaoIdOrderByEscopoAscSocioNomeAscNomeSnapshotAsc(
            UUID prospeccaoId);

    List<ProspeccaoDocumentoEntity> findByProspeccaoId(UUID prospeccaoId);

    void deleteByProspeccaoId(UUID prospeccaoId);
}
