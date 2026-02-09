package com.portal.serasa.infrastructure.persistence;

import com.portal.serasa.application.port.out.ClientRepository;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.mapper.ClientEntityMapper;
import com.portal.serasa.infrastructure.persistence.repository.ClientJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity = jpaRepository.save(entity);
        return mapper.toDomain(entity);
    }

    @Override
    public Optional<Client> findByDocumentNumber(String documentNumber) {
        return jpaRepository.findByDocumentNumber(documentNumber)
                .map(mapper::toDomain);
    }
}
