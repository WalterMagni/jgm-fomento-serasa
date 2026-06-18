package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyNoteAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyNoteAttachmentJpaRepository extends JpaRepository<CompanyNoteAttachmentEntity, UUID> {
    List<CompanyNoteAttachmentEntity> findByNoteIdOrderByCreatedAtAsc(UUID noteId);
    List<CompanyNoteAttachmentEntity> findByNoteIdIn(List<UUID> noteIds);
}
