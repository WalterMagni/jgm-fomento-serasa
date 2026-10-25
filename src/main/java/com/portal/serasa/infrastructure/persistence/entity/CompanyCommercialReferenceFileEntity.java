package com.portal.serasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/** A "colinha" das informações comerciais: um arquivo por empresa. Ver V97. */
@Entity
@Table(name = "company_commercial_reference_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCommercialReferenceFileEntity {

    /** Um por empresa: substituir é gravar por cima, na mesma chave. */
    @Id
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "data", columnDefinition = "bytea", nullable = false)
    private byte[] data;

    @Column(name = "uploaded_by_name", length = 200)
    private String uploadedByName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
