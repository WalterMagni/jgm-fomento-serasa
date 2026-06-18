package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.CompanyDetailEntity;
import com.portal.serasa.infrastructure.persistence.mapper.CompanyDetailEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.CompanyDetailJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompanyDetailRepositoryAdapter implements CompanyDetailRepository {

    private final CompanyDetailJpaRepository jpaRepository;
    private final CompanyDetailEntityMapper mapper;

    @Override
    public CompanyDetail save(CompanyDetail companyDetail) {
        CompanyDetailEntity entity = mapper.toEntity(companyDetail);
        if (entity.getId() == null) {
            entity.setId(UUID.randomUUID());
        }
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<CompanyDetail> findByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber)
                .map(mapper::toDomain);
    }

    @Override
    public List<CompanyDetail> findByDocumentNumberIn(Collection<String> documentNumbers) {
        if (documentNumbers == null || documentNumbers.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByDocumentNumberIn(documentNumbers).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<CompanyDetail> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public long countRegisteredEnrichedClients() {
        return jpaRepository.countRegisteredEnrichedClients();
    }

    @Override
    public void deleteByDocumentNumber(String documentNumber) {
        jpaRepository.deleteByDocumentNumber(documentNumber);
    }

    @Override
    public boolean existsByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber).isPresent();
    }
}
