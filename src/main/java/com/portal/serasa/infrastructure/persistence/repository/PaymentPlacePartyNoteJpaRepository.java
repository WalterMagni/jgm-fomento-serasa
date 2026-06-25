package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlacePartyNoteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentPlacePartyNoteJpaRepository extends JpaRepository<PaymentPlacePartyNoteEntity, UUID> {

    Optional<PaymentPlacePartyNoteEntity> findByPartyTypeAndDocument(String partyType, String document);

    List<PaymentPlacePartyNoteEntity> findByPartyTypeAndDocumentIn(String partyType, List<String> documents);
}
