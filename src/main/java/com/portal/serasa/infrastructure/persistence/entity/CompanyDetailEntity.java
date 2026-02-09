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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "company_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDetailEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "document_number", nullable = false, unique = true, length = 14)
    private String documentNumber;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(length = 255)
    private String alias;

    @Column
    private LocalDate founded;

    @Column(name = "company_name", length = 500)
    private String companyName;

    @Column(name = "company_equity", precision = 20, scale = 2)
    private BigDecimal companyEquity;

    @Column(name = "nature_id")
    private Integer natureId;

    @Column(name = "nature_text", length = 255)
    private String natureText;

    @Column(name = "size_text", length = 100)
    private String sizeText;

    @Column(length = 255)
    private String street;

    @Column(length = 50)
    private String number;

    @Column(length = 255)
    private String details;

    @Column(length = 255)
    private String district;

    @Column(length = 255)
    private String city;

    @Column(length = 10)
    private String state;

    @Column(length = 20)
    private String zip;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> members;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> phones;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<Map<String, Object>> emails;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "main_activity", columnDefinition = "jsonb")
    private Map<String, Object> mainActivity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "side_activities", columnDefinition = "jsonb")
    private List<Map<String, Object>> sideActivities;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
