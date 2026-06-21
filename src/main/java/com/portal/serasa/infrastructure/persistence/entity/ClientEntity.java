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

import jakarta.persistence.EntityListeners;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "clients")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
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

    @Column(name = "client_code", length = 20)
    private String clientCode;

    @Column(name = "origin", length = 20)
    private String origin;

    @Column(length = 500)
    private String name;

    @Column(name = "address_zip", length = 10)
    private String addressZip;

    @Column(name = "address_street", length = 255)
    private String addressStreet;

    @Column(name = "address_number", length = 20)
    private String addressNumber;

    @Column(name = "address_complement", length = 120)
    private String addressComplement;

    @Column(name = "address_district", length = 120)
    private String addressDistrict;

    @Column(name = "address_city", length = 120)
    private String addressCity;

    @Column(name = "address_uf", length = 2)
    private String addressUf;

    @Column(name = "latitude")
    private java.math.BigDecimal latitude;

    @Column(name = "longitude")
    private java.math.BigDecimal longitude;

    @Column(length = 255)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> phones;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;
}
