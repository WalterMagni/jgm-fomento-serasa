package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ApiUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ApiUsageLogJpaRepository extends JpaRepository<ApiUsageLogEntity, UUID> {
}
