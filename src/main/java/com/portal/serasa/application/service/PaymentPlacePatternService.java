package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlacePatternEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlacePatternJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Memória de padrões por par cedente × sacado. Cada decisão do analista recompila as
 * contagens do par (a partir da fonte de verdade — os lançamentos decididos), e o
 * {@link PaymentPlaceScorer} usa o padrão para reforçar a sugestão nas importações
 * seguintes. Documentos são normalizados para só dígitos antes de gravar/consultar.
 */
@Service
@RequiredArgsConstructor
public class PaymentPlacePatternService {

    private final PaymentPlacePatternJpaRepository patternRepository;
    private final PaymentPlaceEntryJpaRepository entryRepository;
    private final com.portal.serasa.application.port.out.CompanyDetailRepository companyDetailRepository;

    /** Nome da empresa pelo documento (best-effort, para a tela de padrões). */
    @Transactional(readOnly = true)
    public String companyName(String document) {
        String doc = digits(document);
        if (doc == null) {
            return null;
        }
        return companyDetailRepository.findByDocumentNumber(doc)
                .map(com.portal.serasa.domain.model.CompanyDetail::getCompanyName)
                .orElse(null);
    }

    /** Documento canônico do par: só dígitos; nulo/vazio → null (não forma par). */
    static String digits(String value) {
        if (value == null) {
            return null;
        }
        String d = value.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    /**
     * Recompila o padrão do par a partir das decisões existentes. Idempotente — pode ser
     * chamado após decidir, decidir em massa ou reabrir um título. Só age quando o par tem
     * cedente E sacado identificados.
     */
    @Transactional
    public void recordPair(String rawClientDocument, String rawPayerDocument) {
        String ced = digits(rawClientDocument);
        String pay = digits(rawPayerDocument);
        if (ced == null || pay == null) {
            return;
        }
        var rows = entryRepository.findPairDecisions(ced, pay);

        int cedente = 0;
        int sacado = 0;
        int inconclusivo = 0;
        String lastDecision = null;
        java.time.LocalDateTime lastAt = null;
        for (Object[] row : rows) {
            String decision = row[0] == null ? null : row[0].toString();
            java.time.LocalDateTime decidedAt = toDateTime(row[1]);
            if ("CEDENTE".equals(decision)) {
                cedente++;
            } else if ("SACADO".equals(decision)) {
                sacado++;
            } else if ("INCONCLUSIVO".equals(decision)) {
                inconclusivo++;
            }
            // Linhas já vêm ordenadas por decided_at desc → a primeira é a última decisão.
            if (lastDecision == null && decision != null) {
                lastDecision = decision;
                lastAt = decidedAt;
            }
        }
        int total = cedente + sacado + inconclusivo;

        PaymentPlacePatternEntity pattern = patternRepository
                .findByClientDocumentAndPayerDocument(ced, pay)
                .orElseGet(() -> PaymentPlacePatternEntity.builder()
                        .clientDocument(ced)
                        .payerDocument(pay)
                        .build());

        pattern.setCedenteCount(cedente);
        pattern.setSacadoCount(sacado);
        pattern.setInconclusivoCount(inconclusivo);
        pattern.setTotalCount(total);
        pattern.setLastDecision(lastDecision);
        pattern.setLastDecidedAt(lastAt);
        patternRepository.save(pattern);
    }

    private static java.time.LocalDateTime toDateTime(Object value) {
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

    /** Recompila TODOS os pares a partir das decisões existentes (conserta dados desatualizados). */
    @Transactional
    public int recomputeAll() {
        var pairs = entryRepository.findDecidedPairs();
        for (Object[] pair : pairs) {
            String ced = pair[0] == null ? null : pair[0].toString();
            String pay = pair[1] == null ? null : pair[1].toString();
            recordPair(ced, pay);
        }
        return pairs.size();
    }

    @Transactional(readOnly = true)
    public Optional<PaymentPlacePatternEntity> lookup(String rawClientDocument, String rawPayerDocument) {
        String ced = digits(rawClientDocument);
        String pay = digits(rawPayerDocument);
        if (ced == null || pay == null) {
            return Optional.empty();
        }
        return patternRepository.findByClientDocumentAndPayerDocument(ced, pay);
    }

    @Transactional(readOnly = true)
    public Page<PaymentPlacePatternEntity> list(String query, int page, int size) {
        String q = query == null ? "" : query.replaceAll("\\D", "");
        String likeQ = "%" + q + "%";
        int safeSize = Math.max(1, Math.min(size, 50));
        return patternRepository.search(q, likeQ, PageRequest.of(Math.max(0, page), safeSize));
    }

    @Transactional(readOnly = true)
    public PatternStats stats() {
        return new PatternStats(patternRepository.count(), patternRepository.countByLockedTrue());
    }

    /** Trava/destrava um par (override forte do analista) e fixa a decisão. */
    @Transactional
    public PaymentPlacePatternEntity setLock(UUID id, boolean locked, String decision, UserEntity user) {
        PaymentPlacePatternEntity pattern = patternRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Padrão não encontrado"));
        pattern.setLocked(locked);
        if (locked) {
            String norm = decision == null ? null : decision.trim().toUpperCase();
            if (!"SACADO".equals(norm) && !"CEDENTE".equals(norm)) {
                throw new IllegalArgumentException("Decisão travada deve ser SACADO ou CEDENTE");
            }
            pattern.setLockedDecision(norm);
            pattern.setLockedByName(user != null ? user.getName() : null);
        } else {
            pattern.setLockedDecision(null);
            pattern.setLockedByName(null);
        }
        return patternRepository.save(pattern);
    }

    public record PatternStats(long totalPatterns, long lockedPatterns) {
    }
}
