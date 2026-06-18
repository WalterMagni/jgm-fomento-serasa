package com.portal.serasa.application.service;

import com.opencsv.exceptions.CsvException;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.infrastructure.integration.csv.CsvClientReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientImportService {

    private static final int BATCH_SIZE = 100;

    private final CsvClientReader csvClientReader;
    private final com.portal.serasa.application.port.out.ClientRepository clientRepository;
    private final MunicipalityGeocoder geocoder;

    /**
     * Importa clientes do CSV com upsert e processamento em lotes.
     * Retorna resumo da importação.
     */
    @Transactional
    public ClientImportResult importFromCsv(InputStream inputStream) throws IOException, CsvException {
        List<Client> clients = csvClientReader.readAndParse(inputStream);
        return persistWithUpsert(clients);
    }

    /**
     * Apenas lê e exibe preview dos dados do CSV (sem persistir).
     */
    public CsvClientReader.CsvClientPreview previewFromCsv(InputStream inputStream, int maxItems)
            throws IOException, CsvException {
        List<Client> clients = csvClientReader.readAndParse(inputStream);
        return csvClientReader.createPreview(clients, maxItems);
    }

    private ClientImportResult persistWithUpsert(List<Client> clients) {
        int created = 0;
        int updated = 0;
        int errors = 0;

        List<Client> batch = new ArrayList<>();

        for (Client client : clients) {
            try {
                batch.add(client);
                if (batch.size() >= BATCH_SIZE) {
                    var result = flushBatch(batch);
                    created += result.created();
                    updated += result.updated();
                    errors += result.errors();
                    batch.clear();
                }
            } catch (Exception e) {
                log.warn("Erro ao processar cliente {}: {}", client.getDocumentNumber(), e.getMessage());
                errors++;
            }
        }

        if (!batch.isEmpty()) {
            var result = flushBatch(batch);
            created += result.created();
            updated += result.updated();
            errors += result.errors();
        }

        log.info("Importação concluída: {} criados, {} atualizados, {} erros",
                created, updated, errors);

        return ClientImportResult.builder()
                .totalProcessed(clients.size())
                .created(created)
                .updated(updated)
                .totalCadastrados(created + updated)
                .errors(errors)
                .build();
    }

    private BatchResult flushBatch(List<Client> batch) {
        int created = 0;
        int updated = 0;
        int errors = 0;

        for (Client client : batch) {
            try {
                applyGeocoding(client);
                var existing = clientRepository.findByDocumentNumber(client.getDocumentNumber());
                if (existing.isPresent()) {
                    // Preserva nome/email/telefones já enriquecidos, mas atualiza
                    // o código do cliente e o endereço (CSV é a fonte oficial destes).
                    Client current = existing.get();
                    current.setClientCode(client.getClientCode());
                    current.setAddressZip(client.getAddressZip());
                    current.setAddressStreet(client.getAddressStreet());
                    current.setAddressNumber(client.getAddressNumber());
                    current.setAddressComplement(client.getAddressComplement());
                    current.setAddressDistrict(client.getAddressDistrict());
                    current.setAddressCity(client.getAddressCity());
                    current.setAddressUf(client.getAddressUf());
                    current.setLatitude(client.getLatitude());
                    current.setLongitude(client.getLongitude());
                    clientRepository.save(current);
                    updated++;
                } else {
                    created++;
                    clientRepository.save(client);
                }
            } catch (Exception e) {
                log.warn("Erro ao salvar cliente {}: {}", client.getDocumentNumber(), e.getMessage());
                errors++;
            }
        }

        return new BatchResult(created, updated, errors);
    }

    /** Resolve lat/lng do município/UF do cliente via centroide IBGE (offline). */
    private void applyGeocoding(Client client) {
        if (client.getAddressCity() == null || client.getAddressUf() == null) {
            return;
        }
        geocoder.resolve(client.getAddressCity() + "/" + client.getAddressUf())
                .ifPresent(coords -> {
                    client.setLatitude(coords.latitude());
                    client.setLongitude(coords.longitude());
                });
    }

    private record BatchResult(int created, int updated, int errors) {
    }

    @lombok.Data
    @lombok.Builder
    public static class ClientImportResult {
        private int totalProcessed;
        private int created;
        private int updated;
        private int totalCadastrados;
        private int errors;
    }
}
