package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEtiquetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LiberacaoEtiquetaJpaRepository extends JpaRepository<LiberacaoEtiquetaEntity, UUID> {

    List<LiberacaoEtiquetaEntity> findAllByOrderByNomeAsc();

    Optional<LiberacaoEtiquetaEntity> findByNomeIgnoreCase(String nome);
}
