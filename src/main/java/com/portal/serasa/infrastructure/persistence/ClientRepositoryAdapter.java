package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.mapper.ClientEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClientRepositoryAdapter implements ClientRepository {

    private final ClientJpaRepository jpaRepository;
    private final ClientEntityMapper mapper;

    @Override
    public Client save(Client client) {
        ClientEntity entity = mapper.toEntity(client);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Client> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Client> findByClientCode(String clientCode) {
        if (clientCode == null || clientCode.isBlank()) {
            return Optional.empty();
        }
        return jpaRepository.findByClientCode(clientCode).map(mapper::toDomain);
    }

    @Override
    public Page<Client> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Client> search(String term, Pageable pageable) {
        return jpaRepository.findByDocumentNumberContainingIgnoreCaseOrNameContainingIgnoreCase(
                term, term, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Client> searchProfiles(String term, String visaoCedente, String analysisStatus, String origin, Pageable pageable) {
        String normalizedTerm = term == null ? "" : term.trim();
        String normalizedVisaoCedente = visaoCedente == null ? "" : visaoCedente.trim().toUpperCase();
        String normalizedAnalysisStatus = analysisStatus == null ? "" : analysisStatus.trim().toUpperCase();
        String normalizedOrigin = origin == null ? "" : origin.trim().toUpperCase();
        return jpaRepository.searchProfiles(
                normalizedTerm,
                normalizedTerm.isBlank(),
                normalizedVisaoCedente,
                normalizedVisaoCedente.isBlank(),
                normalizedAnalysisStatus,
                normalizedAnalysisStatus.isBlank(),
                normalizedOrigin,
                normalizedOrigin.isBlank(),
                pageable
        ).map(mapper::toDomain);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public List<String> findExistingDocumentNumbers(Collection<String> documentNumbers) {
        if (documentNumbers == null || documentNumbers.isEmpty()) {
            return List.of();
        }

        return jpaRepository.findByDocumentNumberIn(documentNumbers).stream()
                .map(ClientEntity::getDocumentNumber)
                .toList();
    }

    @Override
    public List<Client> findByDocumentNumberStartingWith(String documentRoot) {
        if (documentRoot == null || documentRoot.isBlank()) {
            return List.of();
        }

        return jpaRepository.findByDocumentNumberStartingWith(documentRoot).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public void deleteByDocumentNumber(String documentNumber) {
        jpaRepository.deleteByDocumentNumber(documentNumber);
    }
}
