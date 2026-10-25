package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialReferenceFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CompanyCommercialReferenceFileJpaRepository
        extends JpaRepository<CompanyCommercialReferenceFileEntity, UUID> {

    /** Só os dados do arquivo, sem os bytes: o cabeçalho do painel não precisa deles. */
    @Query("""
            select r.fileName as fileName, r.contentType as contentType, r.fileSize as fileSize,
                   r.uploadedByName as uploadedByName, r.uploadedAt as uploadedAt
            from CompanyCommercialReferenceFileEntity r
            where r.clientId = :clientId
            """)
    Optional<ReferenceInfo> findInfoByClientId(@Param("clientId") UUID clientId);

    interface ReferenceInfo {
        String getFileName();

        String getContentType();

        Long getFileSize();

        String getUploadedByName();

        LocalDateTime getUploadedAt();
    }
}
