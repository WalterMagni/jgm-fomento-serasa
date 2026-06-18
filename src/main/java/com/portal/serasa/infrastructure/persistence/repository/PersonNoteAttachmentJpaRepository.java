package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PersonNoteAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PersonNoteAttachmentJpaRepository extends JpaRepository<PersonNoteAttachmentEntity, UUID> {
    List<PersonNoteAttachmentEntity> findByNoteIdOrderByCreatedAtAsc(UUID noteId);
    List<PersonNoteAttachmentEntity> findByNoteIdIn(List<UUID> noteIds);
}
