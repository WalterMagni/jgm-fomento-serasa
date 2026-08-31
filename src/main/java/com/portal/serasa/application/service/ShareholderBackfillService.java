package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Carga inicial do quadro societário para a carteira inteira, a partir da cópia local da
 * Receita. Roda em bean próprio porque {@code @Async} não funciona em auto-invocação, e em
 * background porque são duas consultas por empresa mais uma por sócio — com ~1.500 clientes
 * isso não cabe no tempo de uma requisição.
 *
 * <p>É idempotente: cada empresa reusa o cache por TTL do
 * {@link CompanyShareholderService}, então repetir a carga não repete o trabalho.</p>
 *
 * <p>O CPF completo <b>não</b> é preenchido aqui — ele chega pelo QSA do relatório da Serasa,
 * no fluxo de enriquecimento da empresa. Esta carga popula a máscara, que já basta para
 * cruzar empresas entre si.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareholderBackfillService {

    private final ClientJpaRepository clientRepository;
    private final CompanyShareholderService shareholderService;
    private final com.portal.serasa.infrastructure.integration.cnpj.ReceitaIndexHealth indexHealth;

    /** Impede duas cargas simultâneas martelando a base da Receita. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Getter
    private volatile BackfillStatus lastStatus = BackfillStatus.idle();

    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return {@code false} se já houver uma carga em andamento.
     */
    public boolean start() {
        if (!shareholderService.isAvailable()) {
            throw new IllegalStateException("Base da Receita indisponivel; carga nao iniciada");
        }
        // Sem o índice a busca reversa varre 27M linhas por sócio: a carga da carteira inteira
        // sairia de minutos para horas. Avisa alto, mas não bloqueia — a decisão é do operador.
        indexHealth.missingIndexesWithFix().forEach((name, sql) -> log.warn(
                "Carga de quadro societario iniciando SEM o indice {} — vai levar horas em vez de "
                        + "minutos. Considere cancelar e rodar antes: {}", name, sql));
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        run();
        return true;
    }

    @Async
    void run() {
        LocalDateTime startedAt = LocalDateTime.now();
        int processed = 0;
        int failed = 0;
        Set<String> roots = distinctRoots();
        log.info("Carga de quadro societario iniciada: {} raizes de CNPJ", roots.size());
        try {
            for (String raiz : roots) {
                try {
                    shareholderService.syncRelatedCompanies(raiz);
                    processed++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Carga de quadro societario falhou para raiz={}: {}", raiz, e.getMessage());
                }
                if (processed % 100 == 0 && processed > 0) {
                    log.info("Carga de quadro societario: {}/{} raizes", processed, roots.size());
                }
            }
        } finally {
            lastStatus = new BackfillStatus(startedAt, LocalDateTime.now(), roots.size(), processed, failed);
            running.set(false);
            log.info("Carga de quadro societario concluida: {} processadas, {} falhas", processed, failed);
        }
    }

    /** Uma raiz por grupo — filiais compartilham quadro societário com a matriz. */
    private Set<String> distinctRoots() {
        List<ClientEntity> clients = clientRepository.findAll();
        Set<String> roots = new LinkedHashSet<>();
        for (ClientEntity client : clients) {
            String digits = CpfMask.digitsOnly(client.getDocumentNumber());
            if (digits != null && digits.length() >= 8) {
                roots.add(digits.substring(0, 8));
            }
        }
        return roots;
    }

    public record BackfillStatus(
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int total,
            int processed,
            int failed) {

        static BackfillStatus idle() {
            return new BackfillStatus(null, null, 0, 0, 0);
        }
    }
}
