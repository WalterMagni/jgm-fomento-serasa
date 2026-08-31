package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.ShareholderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareholderJpaRepository extends JpaRepository<ShareholderEntity, UUID> {

    /** Identidade da carga gratuita: máscara + nome. */
    Optional<ShareholderEntity> findByDocumentMaskAndName(String documentMask, String name);

    Optional<ShareholderEntity> findByDocument(String document);

    List<ShareholderEntity> findByDocumentIn(List<String> documents);
}
