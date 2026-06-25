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

/**
 * Observação persistente de uma entidade (cedente ou sacado), identificada pelo
 * documento normalizado (só dígitos). Reaproveitada em todos os títulos da mesma parte.
 */
@Entity
@Table(name = "payment_place_party_notes")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlacePartyNoteEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "party_type", nullable = false, length = 10)
    private String partyType; // CEDENTE | SACADO

    @Column(nullable = false, length = 20)
    private String document; // só dígitos

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "updated_by_name")
    private String updatedByName;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
