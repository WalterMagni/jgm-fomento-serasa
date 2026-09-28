package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.domain.model.prospeccao.EscopoDocumento;
import com.portal.serasa.infrastructure.persistence.entity.DocumentoTipoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoTipoJpaRepository extends JpaRepository<DocumentoTipoEntity, UUID> {

    List<DocumentoTipoEntity> findByAtivoTrueOrderByEscopoAscOrdemAsc();

    List<DocumentoTipoEntity> findByEscopoAndAtivoTrueOrderByOrdemAsc(EscopoDocumento escopo);

    Optional<DocumentoTipoEntity> findByCodigo(String codigo);
}
