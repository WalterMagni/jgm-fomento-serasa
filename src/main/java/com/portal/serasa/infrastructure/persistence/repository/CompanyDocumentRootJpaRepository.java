package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyDocumentRootEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyDocumentRootJpaRepository extends JpaRepository<CompanyDocumentRootEntity, UUID> {

    Optional<CompanyDocumentRootEntity> findByClientId(UUID clientId);

    void deleteByClientId(UUID clientId);
}
