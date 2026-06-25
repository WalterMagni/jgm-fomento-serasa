package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlacePatternEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentPlacePatternJpaRepository extends JpaRepository<PaymentPlacePatternEntity, UUID> {

    Optional<PaymentPlacePatternEntity> findByClientDocumentAndPayerDocument(String clientDocument, String payerDocument);

    long countByLockedTrue();

    /**
     * Lista os padrões para a tela "Padrões aprendidos". Filtro de texto livre casa com
     * qualquer um dos documentos. Ordena por mais decididos e mais recentes primeiro.
     */
    @Query(value = "SELECT * FROM payment_place_patterns p "
            + "WHERE (:q = '' OR p.client_document LIKE :likeQ OR p.payer_document LIKE :likeQ) "
            + "ORDER BY p.total_count DESC, p.last_decided_at DESC NULLS LAST",
            countQuery = "SELECT count(*) FROM payment_place_patterns p "
            + "WHERE (:q = '' OR p.client_document LIKE :likeQ OR p.payer_document LIKE :likeQ)",
            nativeQuery = true)
    Page<PaymentPlacePatternEntity> search(@Param("q") String q,
                                           @Param("likeQ") String likeQ,
                                           Pageable pageable);
}
