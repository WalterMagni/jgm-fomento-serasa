package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ApiRequestCounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ApiRequestCounterJpaRepository extends JpaRepository<ApiRequestCounterEntity, UUID> {

    Optional<ApiRequestCounterEntity> findByProvider(String provider);

    @Modifying
    @Query("UPDATE ApiRequestCounterEntity c SET c.requestCount = c.requestCount + 1, c.lastRequestAt = CURRENT_TIMESTAMP, c.updatedAt = CURRENT_TIMESTAMP WHERE c.provider = :provider")
    int incrementCount(String provider);
}
