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
@Table(name = "company_commercial_information")
@EntityListeners(org.springframework.data.jpa.domain.support.AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyCommercialInformationEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;

    @Column(name = "operation_date", length = 10)
    private String operationDate;

    @Column(name = "operation_type", length = 50)
    private String operationType;

    @Column(name = "partner")
    private String partner;

    @Column(name = "customer_since", length = 10)
    private String customerSince;

    @Column(name = "last_operation_date", length = 10)
    private String lastOperationDate;

    @Column(name = "last_operation_value", length = 50)
    private String lastOperationValue;

    @Column(name = "credit_limit", length = 50)
    private String creditLimit;

    @Column(name = "duplicate_risk", length = 50)
    private String duplicateRisk;

    @Column(name = "check_risk", length = 50)
    private String checkRisk;

    @Column(name = "commission_risk", length = 50)
    private String commissionRisk;

    @Column(name = "overdue_date", length = 10)
    private String overdueDate;

    @Column(name = "overdue_value", length = 50)
    private String overdueValue;

    @Column(name = "duplicate_due_date", length = 10)
    private String duplicateDueDate;

    @Column(name = "concentration", length = 50)
    private String concentration;

    @Column(name = "vop", length = 50)
    private String vop;

    @Column(name = "punctual_percentage", length = 20)
    private String punctualPercentage;

    @Column(name = "delay_percentage", length = 20)
    private String delayPercentage;

    @Column(name = "notary_percentage", length = 20)
    private String notaryPercentage;

    @Column(name = "repurchase_percentage", length = 20)
    private String repurchasePercentage;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @org.springframework.data.annotation.CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @org.springframework.data.annotation.LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
