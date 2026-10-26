package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.EmpresaLimiteSacadoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface EmpresaLimiteSacadoJpaRepository extends JpaRepository<EmpresaLimiteSacadoEntity, String> {

    List<EmpresaLimiteSacadoEntity> findByCnpjIn(Collection<String> cnpjs);
}
