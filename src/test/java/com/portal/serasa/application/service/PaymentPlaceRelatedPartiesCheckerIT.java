package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceBatchEntity;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceBatchJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Roda a verificação de partes ligadas sobre um lote real já importado — o mesmo caminho do
 * endpoint {@code POST /praca-pagamento/lotes/{id}/partes-ligadas}. Não depende de importar
 * PDF novo, que é justamente o que não se tem à mão fora do dia a dia da operação.
 */
@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "receita.it", matches = "true")
class PaymentPlaceRelatedPartiesCheckerIT {

    @Autowired
    private PaymentPlaceRelatedPartiesChecker checker;

    @Autowired
    private PaymentPlaceBatchJpaRepository batchRepository;

    @Autowired
    private PaymentPlaceEntryJpaRepository entryRepository;

    /**
     * Controle positivo: sem ele o teste acima só provaria que a verificação roda, não que
     * detecta. Cria um lote descartável com um par que sabidamente compartilha sócio
     * (ALFA FOODS × CIA DOS PAES, ambas da carteira) e remove tudo ao final.
     */
    @Test
    @DisplayName("detecta e descreve o socio em comum entre cedente e sacado")
    void shouldFlagEntryWhoseCedenteAndSacadoShareShareholder() {
        PaymentPlaceBatchEntity batch = batchRepository.save(PaymentPlaceBatchEntity.builder()
                .fileName("teste-partes-ligadas.pdf")
                .importedAt(LocalDateTime.now())
                .status("IMPORTADO")
                .totalEntries(1)
                .auditEntries(0)
                .unlocatedAgencyEntries(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        PaymentPlaceEntryEntity entry = entryRepository.save(PaymentPlaceEntryEntity.builder()
                .batchId(batch.getId())
                .section("TESTE")
                .externalId("teste-partes-ligadas")
                .analysisStatus("PENDENTE")
                .clientDocument("50738256000100")   // ALFA FOODS DISTRIBUIDORA
                .payerDocument("51492464000100")    // CIA DOS PAES
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        try {
            checker.checkBatch(batch.getId());

            await().atMost(Duration.ofMinutes(1)).pollInterval(Duration.ofSeconds(1)).untilAsserted(() -> {
                PaymentPlaceEntryEntity reloaded = entryRepository.findById(entry.getId()).orElseThrow();
                assertThat(reloaded.getRelatedPartiesCheckedAt()).isNotNull();
                assertThat(reloaded.isRelatedParties()).isTrue();
                // O alerta precisa dizer QUEM liga as partes, não só que existe vínculo.
                assertThat(reloaded.getRelatedPartiesDetail()).contains("RODRIGO OLIVEIRA CAMPOS");
            });
        } finally {
            entryRepository.deleteById(entry.getId());
            batchRepository.deleteById(batch.getId());
        }
    }

    @Test
    @DisplayName("marca partes ligadas em todo lancamento cruzavel do lote")
    void shouldCheckEveryCrossableEntryOfTheBatch() {
        UUID batchId = batchRepository.findAll().stream()
                .map(b -> b.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Nenhum lote importado para verificar"));

        checker.checkBatch(batchId);

        // O checker é @Async: espera ele terminar antes de conferir o resultado.
        await().atMost(Duration.ofMinutes(2)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
            List<PaymentPlaceEntryEntity> crossable = entryRepository.findByBatchIdOrderByCreatedAtAsc(batchId)
                    .stream()
                    .filter(e -> e.getClientDocument() != null && e.getPayerDocument() != null)
                    .toList();
            assertThat(crossable).isNotEmpty();
            assertThat(crossable).allSatisfy(e -> assertThat(e.getRelatedPartiesCheckedAt()).isNotNull());
        });

        // Detalhe e flag têm de andar juntos: marcado sem nomes seria alerta sem explicação.
        entryRepository.findByBatchIdOrderByCreatedAtAsc(batchId).forEach(e -> {
            if (e.isRelatedParties()) {
                assertThat(e.getRelatedPartiesDetail()).isNotBlank();
            } else {
                assertThat(e.getRelatedPartiesDetail()).isNull();
            }
        });
    }
}
