package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Marca os lançamentos em que cedente e sacado compartilham sócio.
 *
 * <p>Em fomento isso é o indício clássico de duplicata simulada: o título é emitido de uma
 * empresa contra outra do mesmo dono e vendido como recebível. Deliberadamente <b>não</b> entra
 * no {@link PaymentPlaceScorer}: o score decide sacado × cedente, e um vínculo societário não
 * pesa para nenhum dos dois lados — misturar contaminaria a sugestão de praça.</p>
 *
 * <p>Roda assíncrono depois do import, junto do enriquecimento de agência, porque depende de
 * sincronizar o quadro societário de cada empresa contra a cópia local da Receita. O trabalho é
 * deduplicado por CNPJ: um lote costuma repetir muito o mesmo cedente.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPlaceRelatedPartiesChecker {

    private final PaymentPlaceEntryJpaRepository entryRepository;
    private final CompanyShareholderService shareholderService;

    @Async
    @Transactional
    public void checkBatch(UUID batchId) {
        if (!shareholderService.isAvailable()) {
            log.info("Verificacao de partes ligadas ignorada: base da Receita indisponivel");
            return;
        }
        List<PaymentPlaceEntryEntity> entries = entryRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        Set<String> synced = new HashSet<>();
        LocalDateTime now = LocalDateTime.now();
        int flagged = 0;

        for (PaymentPlaceEntryEntity entry : entries) {
            String clientDocument = entry.getClientDocument();
            String payerDocument = entry.getPayerDocument();
            if (isBlank(clientDocument) || isBlank(payerDocument)) {
                continue;
            }
            try {
                // Só o primeiro salto: para cruzar duas empresas bastam os sócios de cada uma.
                syncOnce(synced, clientDocument);
                syncOnce(synced, payerDocument);

                List<String> shared = shareholderService.findSharedShareholders(clientDocument, payerDocument);
                entry.setRelatedParties(!shared.isEmpty());
                entry.setRelatedPartiesDetail(shared.isEmpty() ? null : String.join(", ", shared));
                entry.setRelatedPartiesCheckedAt(now);
                if (!shared.isEmpty()) {
                    flagged++;
                    log.warn("Partes ligadas no lancamento {}: cedente {} e sacado {} compartilham {}",
                            entry.getId(), clientDocument, payerDocument, shared);
                }
            } catch (Exception ex) {
                // Sinal auxiliar: falhar aqui não pode comprometer o lote inteiro.
                log.warn("Falha ao verificar partes ligadas no lancamento {}: {}", entry.getId(), ex.getMessage());
            }
        }
        log.info("Verificacao de partes ligadas do lote {}: {} lancamentos, {} marcados",
                batchId, entries.size(), flagged);
    }

    /** Sincroniza o quadro societário de um CNPJ uma única vez por lote. */
    private void syncOnce(Set<String> synced, String cnpj) {
        if (synced.add(cnpj)) {
            shareholderService.syncShareholders(cnpj);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
