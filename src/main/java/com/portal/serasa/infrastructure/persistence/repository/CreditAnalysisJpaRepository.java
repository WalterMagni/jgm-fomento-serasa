package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditAnalysisJpaRepository extends JpaRepository<CreditAnalysisEntity, Long> {

    List<CreditAnalysisEntity> findByCnpj(String cnpj);

    void deleteByCnpj(String cnpj);
}
