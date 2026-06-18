package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.StandardTermEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StandardTermRepository extends JpaRepository<StandardTermEntity, Long> {
    Optional<StandardTermEntity> findByCnpj(String cnpj);
}
