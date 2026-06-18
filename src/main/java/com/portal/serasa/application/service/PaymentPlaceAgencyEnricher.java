package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.integration.bacen.BacenAgencyClient;
import com.portal.serasa.infrastructure.persistence.entity.PaymentPlaceEntryEntity;
import com.portal.serasa.infrastructure.persistence.repository.PaymentPlaceEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enriquecimento da agência via cadastro Bacen, isolado em bean próprio para
 * permitir execução assíncrona (proxy @Async não funciona em auto-invocação).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPlaceAgencyEnricher {

    private final MunicipalityGeocoder geocoder;
    private final BancoCompeRegistry bancoCompeRegistry;
    private final BacenAgencyClient bacenAgencyClient;
    private final PaymentPlaceScorer scorer;
    private final PaymentPlaceEntryJpaRepository entryRepository;

    /** Enriquece todas as agências de um lote em background (dedupe por banco+agência). */
    @Async
    @Transactional
    public void enrichBatch(UUID batchId) {
        List<PaymentPlaceEntryEntity> entries = entryRepository.findByBatchIdOrderByCreatedAtAsc(batchId);
        Map<String, Optional<BacenAgencyClient.BacenAgency>> cache = new HashMap<>();
        for (PaymentPlaceEntryEntity entry : entries) {
            applyBacenAgency(entry, cache);
        }
        entryRepository.saveAll(entries);
        log.info("Enriquecimento Bacen concluído para lote {} ({} lançamentos, {} agências consultadas)",
                batchId, entries.size(), cache.size());
    }

    /** Enriquece um único lançamento (refresh manual). */
    @Transactional
    public PaymentPlaceEntryEntity enrichSingle(UUID entryId) {
        PaymentPlaceEntryEntity entry = entryRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Lançamento de praça de pagamento não encontrado"));
        applyBacenAgency(entry, new HashMap<>());
        return entryRepository.save(entry);
    }

    private void applyBacenAgency(PaymentPlaceEntryEntity entry,
                                  Map<String, Optional<BacenAgencyClient.BacenAgency>> cache) {
        String cnpjBase = bancoCompeRegistry.cnpjBase(entry.getBankCode()).orElse(null);
        String codigoCompe = normalizeAgencyCode(entry.getAgencyCode());
        entry.setAgencyEnrichedAt(LocalDateTime.now());
        if (cnpjBase == null || codigoCompe == null) {
            return;
        }

        String cacheKey = cnpjBase + "|" + codigoCompe;
        Optional<BacenAgencyClient.BacenAgency> result =
                cache.computeIfAbsent(cacheKey, k -> bacenAgencyClient.fetchAgency(cnpjBase, codigoCompe));

        result.ifPresent(agency -> {
            String address = AddressFormat.format(agency.endereco(), agency.numero(), agency.complemento(),
                    agency.bairro(), agency.municipio(), agency.uf(), agency.cep());
            entry.setBacenAgencyName(agency.nomeAgencia());
            entry.setBacenInstitutionName(agency.nomeIf());
            entry.setBacenAgencyCity(blankToNull(agency.municipio()));
            entry.setBacenAgencyZipCode(blankToNull(agency.cep()));
            entry.setBacenAgencyAddress(address);
            entry.setAgencyAddressResolved(address);

            if (agency.municipio() != null && agency.uf() != null) {
                geocoder.resolve(agency.municipio() + "/" + agency.uf()).ifPresent(coords -> {
                    entry.setAgencyLatitude(coords.latitude());
                    entry.setAgencyLongitude(coords.longitude());
                    entry.setDistanceClientAgencyKm(geocoder.distanceKm(
                            coordsOf(entry.getClientLatitude(), entry.getClientLongitude()), coords));
                    entry.setDistanceAgencyPayerKm(geocoder.distanceKm(
                            coords, coordsOf(entry.getPayerLatitude(), entry.getPayerLongitude())));
                });
            }
        });

        // Re-score com a distância da agência real (município do Bacen).
        scorer.apply(entry);
    }

    private MunicipalityGeocoder.Coordinates coordsOf(BigDecimal lat, BigDecimal lng) {
        return (lat == null || lng == null) ? null : new MunicipalityGeocoder.Coordinates(lat, lng);
    }

    private String normalizeAgencyCode(String agencyCode) {
        if (agencyCode == null) {
            return null;
        }
        String digits = agencyCode.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        return String.format("%05d", Integer.parseInt(digits));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
