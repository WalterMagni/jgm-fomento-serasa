package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyCommercialInformationJpaRepository extends JpaRepository<CompanyCommercialInformationEntity, UUID> {

    List<CompanyCommercialInformationEntity> findByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<CompanyCommercialInformationEntity> findAllByOrderByCreatedAtDesc();
}
