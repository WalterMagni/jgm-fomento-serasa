package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoArquivoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProspeccaoArquivoJpaRepository
        extends JpaRepository<ProspeccaoArquivoEntity, UUID> {

    List<ProspeccaoArquivoEntity> findByProspeccaoIdAndRemovidoEmIsNullOrderByEnviadoEmDesc(
            UUID prospeccaoId);

    List<ProspeccaoArquivoEntity> findByDocumentoIdAndRemovidoEmIsNullOrderByVersaoDesc(
            UUID documentoId);

    List<ProspeccaoArquivoEntity> findByProspeccaoIdInAndRemovidoEmIsNull(
            java.util.Collection<UUID> prospeccaoIds);

    /** Maior versão já usada para o item, inclusive versões removidas logicamente. */
    long countByDocumentoId(UUID documentoId);
}
