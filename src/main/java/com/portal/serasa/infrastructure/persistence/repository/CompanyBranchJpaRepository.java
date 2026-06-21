package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyBranchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyBranchJpaRepository extends JpaRepository<CompanyBranchEntity, UUID> {

    List<CompanyBranchEntity> findByCnpjRaiz(String cnpjRaiz);

    @Query("select max(b.fetchedAt) from CompanyBranchEntity b where b.cnpjRaiz = :raiz")
    Optional<LocalDateTime> findLastFetchedAt(@Param("raiz") String raiz);

    @Modifying
    void deleteByCnpjRaiz(String cnpjRaiz);
}
