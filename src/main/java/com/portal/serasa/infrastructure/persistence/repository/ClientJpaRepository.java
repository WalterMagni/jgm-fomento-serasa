package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientJpaRepository extends JpaRepository<ClientEntity, UUID> {

    Optional<ClientEntity> findByDocumentNumber(String documentNumber);
}
