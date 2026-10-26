package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.EmpresaLimiteSacadoHistoricoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmpresaLimiteSacadoHistoricoJpaRepository extends JpaRepository<EmpresaLimiteSacadoHistoricoEntity, UUID> {

    List<EmpresaLimiteSacadoHistoricoEntity> findTop10ByCnpjOrderByCriadoEmDesc(String cnpj);
}
