package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyPartnerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyPartnerJpaRepository extends JpaRepository<CompanyPartnerEntity, UUID> {

    @Query("select p from CompanyPartnerEntity p where p.cnpjA = :cnpj or p.cnpjB = :cnpj")
    List<CompanyPartnerEntity> findAllByCnpj(@Param("cnpj") String cnpj);

    Optional<CompanyPartnerEntity> findByCnpjAAndCnpjB(String cnpjA, String cnpjB);
}
