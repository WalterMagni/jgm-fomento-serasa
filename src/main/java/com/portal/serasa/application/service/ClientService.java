package com.portal.serasa.application.service;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final CompanyDetailRepository companyDetailRepository;
    private final CreditAnalysisRepository creditAnalysisRepository;

    @Transactional
    public Client create(Client client) {
        String doc = normalizeDocument(client.getDocumentNumber());
        if (doc == null || doc.length() != 14) {
            throw new IllegalArgumentException("Documento deve conter 14 dígitos (CNPJ)");
        }
        if (clientRepository.findByDocumentNumber(doc).isPresent()) {
            throw new IllegalArgumentException("Já existe cliente cadastrado com documento: " + doc);
        }
        client.setDocumentNumber(doc);
        return clientRepository.save(client);
    }

    public Optional<Client> findById(java.util.UUID id) {
        return clientRepository.findById(id);
    }

    public Optional<Client> findByDocumentNumber(String documentNumber) {
        return clientRepository.findByDocumentNumber(normalizeDocument(documentNumber));
    }

    @Transactional
    public Client update(java.util.UUID id, Client updates) {
        Client existing = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + id));
        applyUpdates(existing, updates);
        return clientRepository.save(existing);
    }

    /** Remove cliente e todos os dados relacionados em cascata (por documento). */
    @Transactional
    public void deleteById(java.util.UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + id));
        deleteCascade(client.getDocumentNumber());
    }

    /** Remove cliente por documento e todos os dados relacionados em cascata. */
    @Transactional
    public void deleteByDocumentNumber(String documentNumber) {
        String doc = normalizeDocument(documentNumber);
        if (!clientRepository.findByDocumentNumber(doc).isPresent()) {
            throw new EntityNotFoundException("Cliente não encontrado para documento: " + doc);
        }
        deleteCascade(doc);
    }

    private void deleteCascade(String documentNumber) {
        creditAnalysisRepository.deleteAllByCnpj(documentNumber);
        companyDetailRepository.deleteByDocumentNumber(documentNumber);
        clientRepository.deleteByDocumentNumber(documentNumber);
        log.info("Cliente e dados relacionados removidos em cascata: {}", documentNumber);
    }

    private void applyUpdates(Client target, Client source) {
        if (source.getName() != null) target.setName(source.getName());
        if (source.getEmail() != null) target.setEmail(source.getEmail());
        if (source.getPhones() != null) target.setPhones(source.getPhones());
    }

    private String normalizeDocument(String doc) {
        if (doc == null || doc.isBlank()) return null;
        String digits = doc.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : digits;
    }
}
