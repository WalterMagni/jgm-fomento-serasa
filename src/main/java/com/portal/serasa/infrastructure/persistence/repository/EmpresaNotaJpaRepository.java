package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.EmpresaNotaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmpresaNotaJpaRepository extends JpaRepository<EmpresaNotaEntity, String> {

    List<EmpresaNotaEntity> findByCnpjIn(Collection<String> cnpjs);
}
