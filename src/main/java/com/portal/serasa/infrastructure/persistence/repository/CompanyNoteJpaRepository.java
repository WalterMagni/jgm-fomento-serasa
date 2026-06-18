package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.CompanyNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyNoteJpaRepository extends JpaRepository<CompanyNoteEntity, UUID> {

    List<CompanyNoteEntity> findByClientIdOrderByCreatedAtDesc(UUID clientId);
}
