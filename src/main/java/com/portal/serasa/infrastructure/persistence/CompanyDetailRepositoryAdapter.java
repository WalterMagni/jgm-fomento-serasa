package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.CompanyDetailEntity;
import com.portal.serasa.infrastructure.persistence.mapper.CompanyDetailEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.CompanyDetailJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

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
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<CompanyDetail> findByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber)
                .map(mapper::toDomain);
    }
}
