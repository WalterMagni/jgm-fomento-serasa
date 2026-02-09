package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyDetailJpaRepository extends JpaRepository<CompanyDetailEntity, UUID> {

    Optional<CompanyDetailEntity> findByDocumentNumber(String documentNumber);
}
