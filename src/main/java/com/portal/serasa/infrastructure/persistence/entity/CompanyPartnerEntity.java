package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Vínculo manual entre duas empresas parceiras. Os CNPJs são guardados em ordem
 * canônica (cnpjA < cnpjB) para que o par seja único e o vínculo valha nos dois sentidos.
 */
@Entity
@Table(name = "company_partners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPartnerEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "cnpj_a", nullable = false, length = 14)
    private String cnpjA;

    @Column(name = "cnpj_b", nullable = false, length = 14)
    private String cnpjB;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
