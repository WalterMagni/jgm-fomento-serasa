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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_usage_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageLogEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "company_name", length = 500)
    private String companyName;

    @Column(name = "cnpj", length = 14)
    private String cnpj;

    @Column(name = "query_type", length = 20, nullable = false)
    private String queryType;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal cost;

    @Column(name = "queried_at", nullable = false)
    private LocalDateTime queriedAt;
}
