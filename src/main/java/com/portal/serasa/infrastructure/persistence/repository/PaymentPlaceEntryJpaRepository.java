package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentPlaceEntryJpaRepository extends JpaRepository<PaymentPlaceEntryEntity, UUID> {

    List<PaymentPlaceEntryEntity> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    long countByPayerDocumentAndAnalystDecision(String payerDocument, String analystDecision);

    long countByClientCodeAndAnalystDecision(String clientCode, String analystDecision);
}
