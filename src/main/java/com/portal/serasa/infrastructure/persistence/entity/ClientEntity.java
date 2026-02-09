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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "document_number", nullable = false, unique = true, length = 14)
    private String documentNumber;

    @Column(length = 500)
    private String name;

    @Column(length = 255)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> phones;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
