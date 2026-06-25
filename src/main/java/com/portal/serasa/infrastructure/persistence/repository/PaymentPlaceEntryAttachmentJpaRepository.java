package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PaymentPlaceEntryAttachmentJpaRepository extends JpaRepository<PaymentPlaceEntryAttachmentEntity, UUID> {

    List<PaymentPlaceEntryAttachmentEntity> findByEntryIdOrderByCreatedAtAsc(UUID entryId);

    long countByEntryId(UUID entryId);

    /** Conta anexos agrupados por título (para o indicativo nas listas). */
    @Query("SELECT a.entryId AS entryId, COUNT(a) AS total FROM PaymentPlaceEntryAttachmentEntity a "
            + "WHERE a.entryId IN :entryIds GROUP BY a.entryId")
    List<EntryAttachmentCount> countByEntryIdIn(List<UUID> entryIds);

    interface EntryAttachmentCount {
        UUID getEntryId();
        long getTotal();
    }
}
