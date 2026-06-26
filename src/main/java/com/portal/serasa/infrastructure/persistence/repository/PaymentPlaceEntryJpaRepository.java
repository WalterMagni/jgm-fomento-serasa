package com.portal.serasa.infrastructure.persistence.repository;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PaymentPlaceEntryJpaRepository extends JpaRepository<PaymentPlaceEntryEntity, UUID> {

    List<PaymentPlaceEntryEntity> findByBatchIdOrderByCreatedAtAsc(UUID batchId);

    long countByPayerDocumentAndAnalystDecision(String payerDocument, String analystDecision);

    long countByClientCodeAndAnalystDecision(String clientCode, String analystDecision);

    /**
     * Decisões (decisão + data) dos lançamentos de um par cedente×sacado, mais recentes primeiro.
     * Documentos normalizados no SQL (payer vem mascarado do PDF). O serviço conta em Java —
     * evita projeção de interface sobre native query (que falha silenciosamente com FILTER/array_agg).
     */
    @Query(value = "SELECT e.analyst_decision, e.decided_at, e.bank_name "
            + "FROM payment_place_entries e "
            + "WHERE e.analyst_decision IS NOT NULL "
            + "AND regexp_replace(COALESCE(e.client_document, ''), '\\D', '', 'g') = :ced "
            + "AND regexp_replace(COALESCE(e.payer_document, ''), '\\D', '', 'g') = :pay "
            + "AND COALESCE(e.bank_code, '') = :bank "
            + "AND COALESCE(e.agency_code, '') = :agency "
            + "ORDER BY e.decided_at DESC NULLS LAST",
            nativeQuery = true)
    java.util.List<Object[]> findContextDecisions(@Param("ced") String clientDocument,
                                                  @Param("pay") String payerDocument,
                                                  @Param("bank") String bankCode,
                                                  @Param("agency") String agencyCode);

    /**
     * Lançamentos AINDA pendentes (sem decisão) de um par cedente×sacado (documentos só dígitos).
     * Usado para re-scorar os irmãos assim que o padrão do par muda → a sugestão do cérebro
     * aparece sozinha sem reimportar.
     */
    @Query(value = "SELECT * FROM payment_place_entries e "
            + "WHERE e.analyst_decision IS NULL "
            + "AND regexp_replace(COALESCE(e.client_document, ''), '\\D', '', 'g') = :ced "
            + "AND regexp_replace(COALESCE(e.payer_document, ''), '\\D', '', 'g') = :pay "
            + "AND COALESCE(e.bank_code, '') = :bank "
            + "AND COALESCE(e.agency_code, '') = :agency",
            nativeQuery = true)
    java.util.List<PaymentPlaceEntryEntity> findPendingByContext(@Param("ced") String clientDocument,
                                                                 @Param("pay") String payerDocument,
                                                                 @Param("bank") String bankCode,
                                                                 @Param("agency") String agencyCode);

    /** Contextos distintos (cedente×sacado×banco×agência) com ao menos uma decisão — para recompilar tudo. */
    @Query(value = "SELECT DISTINCT "
            + "regexp_replace(COALESCE(e.client_document, ''), '\\D', '', 'g') AS ced, "
            + "regexp_replace(COALESCE(e.payer_document, ''), '\\D', '', 'g') AS pay, "
            + "COALESCE(e.bank_code, '') AS bank, "
            + "COALESCE(e.agency_code, '') AS agency "
            + "FROM payment_place_entries e "
            + "WHERE e.analyst_decision IS NOT NULL "
            + "AND regexp_replace(COALESCE(e.client_document, ''), '\\D', '', 'g') <> '' "
            + "AND regexp_replace(COALESCE(e.payer_document, ''), '\\D', '', 'g') <> ''",
            nativeQuery = true)
    java.util.List<Object[]> findDecidedContexts();

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

    /**
     * Lançamentos ainda sem cedente resolvido (client_document nulo) cujo código,
     * normalizado (só dígitos, sem zeros à esquerda), bate com :code. Usado para
     * propagar a vinculação de um CNPJ a todos os títulos do mesmo cedente.
     */
    @Query(value = "SELECT * FROM payment_place_entries e "
            + "WHERE e.client_document IS NULL "
            + "AND regexp_replace(regexp_replace(COALESCE(e.client_code, ''), '\\D', '', 'g'), '^0+', '') = :code",
            nativeQuery = true)
    List<PaymentPlaceEntryEntity> findUnresolvedByNormalizedClientCode(@Param("code") String code);

    /**
     * Lançamentos marcados como INCONCLUSIVO (de qualquer lote), filtrados pela data
     * da decisão. Página ordenada por decisão mais recente. Filtros nulos = sem limite.
     */
    @Query("SELECT e FROM PaymentPlaceEntryEntity e WHERE e.analystDecision = 'INCONCLUSIVO' "
            + "AND e.decidedAt >= :from AND e.decidedAt <= :to "
            + "ORDER BY e.decidedAt DESC")
    Page<PaymentPlaceEntryEntity> findInconclusivos(@Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to,
                                                    Pageable pageable);

    /**
     * Histórico/biblioteca: busca lançamentos de TODOS os lotes pela data de importação
     * do lote e por texto livre (sacado, cedente, banco/agência, nº do título, documentos).
     * {@code q} vazio = sem filtro de texto; {@code likeQ} deve vir como "%texto%".
     */
    @Query(value = "SELECT e.* FROM payment_place_entries e "
            + "JOIN payment_place_batches b ON b.id = e.batch_id "
            + "WHERE b.imported_at >= :from AND b.imported_at <= :to "
            + "AND (:q = '' "
            + "  OR e.payer_name ILIKE :likeQ OR e.client_name ILIKE :likeQ "
            + "  OR e.bank_agency ILIKE :likeQ OR e.title_number ILIKE :likeQ "
            + "  OR e.payer_document ILIKE :likeQ OR e.client_document ILIKE :likeQ "
            + "  OR e.client_code ILIKE :likeQ OR e.bank_name ILIKE :likeQ) "
            + "ORDER BY b.imported_at DESC, e.created_at ASC",
            countQuery = "SELECT count(*) FROM payment_place_entries e "
            + "JOIN payment_place_batches b ON b.id = e.batch_id "
            + "WHERE b.imported_at >= :from AND b.imported_at <= :to "
            + "AND (:q = '' "
            + "  OR e.payer_name ILIKE :likeQ OR e.client_name ILIKE :likeQ "
            + "  OR e.bank_agency ILIKE :likeQ OR e.title_number ILIKE :likeQ "
            + "  OR e.payer_document ILIKE :likeQ OR e.client_document ILIKE :likeQ "
            + "  OR e.client_code ILIKE :likeQ OR e.bank_name ILIKE :likeQ)",
            nativeQuery = true)
    Page<PaymentPlaceEntryEntity> searchHistory(@Param("from") LocalDateTime from,
                                                @Param("to") LocalDateTime to,
                                                @Param("q") String q,
                                                @Param("likeQ") String likeQ,
                                                Pageable pageable);
}
