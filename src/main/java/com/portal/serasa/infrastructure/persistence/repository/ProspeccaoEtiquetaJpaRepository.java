package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEtiquetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProspeccaoEtiquetaJpaRepository extends JpaRepository<ProspeccaoEtiquetaEntity, UUID> {

    List<ProspeccaoEtiquetaEntity> findAllByOrderByNomeAsc();

    Optional<ProspeccaoEtiquetaEntity> findByNomeIgnoreCase(String nome);
}
