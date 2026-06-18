package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.PersonAnalysisRepository;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.mapper.PersonAnalysisEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PersonAnalysisRepositoryAdapter implements PersonAnalysisRepository {

    private final PersonAnalysisJpaRepository jpaRepository;
    private final PersonAnalysisEntityMapper mapper;

    @Override
    public PersonAnalysis save(PersonAnalysis personAnalysis) {
        PersonAnalysisEntity entity = mapper.toEntity(personAnalysis);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<PersonAnalysis> findLatestByCpf(String cpf) {
        return jpaRepository.findFirstByCpfOrderByConsultaEmDesc(cpf)
                .map(mapper::toDomain);
    }

    @Override
    public List<PersonAnalysis> findByCpf(String cpf) {
        return jpaRepository.findByCpf(cpf).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, PersonAnalysis> findLatestByCpfIn(Collection<String> cpfs) {
        if (cpfs == null || cpfs.isEmpty()) return Map.of();
        return jpaRepository.findLatestByCpfIn(cpfs).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toMap(PersonAnalysis::getCpf, pa -> pa, (a, b) -> a));
    }

    @Override
    public Page<PersonAnalysis> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<PersonAnalysis> search(String term, Pageable pageable) {
        return jpaRepository
                .findByCpfContainingIgnoreCaseOrPersonNameContainingIgnoreCase(term, term, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public void deleteByCpf(String cpf) {
        jpaRepository.deleteByCpf(cpf);
    }
}
