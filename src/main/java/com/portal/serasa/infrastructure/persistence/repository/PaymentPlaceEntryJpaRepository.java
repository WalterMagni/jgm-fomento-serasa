package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentPlaceEntryJpaRepository extends JpaRepository<PaymentPlaceEntryEntity, UUID> {

    List<PaymentPlaceEntryEntity> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    long countByPayerDocumentAndAnalystDecision(String payerDocument, String analystDecision);

    long countByClientCodeAndAnalystDecision(String clientCode, String analystDecision);

    List<PaymentPlaceEntryEntity> findByClientDocumentAndAnalystDecisionIsNotNullOrderByDecidedAtDesc(String clientDocument);

    /**
     * Títulos decididos como CEDENTE em que a empresa (CNPJ só dígitos) participa como cedente
     * OU como sacado. Normaliza ambos os documentos no SQL porque payer_document costuma vir
     * mascarado do PDF ("58.277.872/0009-39") e client_document vem só com dígitos.
     */
    @Query(value = "SELECT * FROM payment_place_entries e "
            + "WHERE e.analyst_decision = 'CEDENTE' "
            + "AND (regexp_replace(COALESCE(e.client_document, ''), '\\D', '', 'g') = :doc "
            + "OR regexp_replace(COALESCE(e.payer_document, ''), '\\D', '', 'g') = :doc) "
            + "ORDER BY e.decided_at DESC NULLS LAST", nativeQuery = true)
    List<PaymentPlaceEntryEntity> findCedenteDecidedForCompany(@Param("doc") String doc);
}
