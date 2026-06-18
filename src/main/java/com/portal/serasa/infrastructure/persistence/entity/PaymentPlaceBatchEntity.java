package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_place_batches")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceBatchEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "imported_by_user_id")
    private UUID importedByUserId;

    @Column(name = "imported_by_name")
    private String importedByName;

    @Column(name = "imported_by_email")
    private String importedByEmail;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "total_entries", nullable = false)
    private int totalEntries;

    @Column(name = "audit_entries", nullable = false)
    private int auditEntries;

    @Column(name = "unlocated_agency_entries", nullable = false)
    private int unlocatedAgencyEntries;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
