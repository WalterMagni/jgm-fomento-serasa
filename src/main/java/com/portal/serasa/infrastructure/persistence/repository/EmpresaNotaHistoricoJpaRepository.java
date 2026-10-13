package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.EmpresaNotaHistoricoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmpresaNotaHistoricoJpaRepository extends JpaRepository<EmpresaNotaHistoricoEntity, UUID> {

    List<EmpresaNotaHistoricoEntity> findTop10ByCnpjOrderByCriadoEmDesc(String cnpj);
}
