package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyCommercialInformationAttachmentJpaRepository
        extends JpaRepository<CompanyCommercialInformationAttachmentEntity, UUID> {

    /** Só os dados do anexo, sem o arquivo: a listagem não precisa carregar os bytes. */
    @Query("""
            select a.id as id, a.recordId as recordId, a.fileName as fileName,
                   a.contentType as contentType, a.fileSize as fileSize
            from CompanyCommercialInformationAttachmentEntity a
            where a.recordId in :recordIds
            order by a.createdAt
            """)
    List<AttachmentInfo> findInfoByRecordIdIn(@Param("recordIds") Collection<UUID> recordIds);

    Optional<CompanyCommercialInformationAttachmentEntity> findByIdAndRecordId(UUID id, UUID recordId);

    long countByRecordId(UUID recordId);

    interface AttachmentInfo {
        UUID getId();

        UUID getRecordId();

        String getFileName();

        String getContentType();

        Long getFileSize();
    }
}
