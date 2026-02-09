package com.portal.serasa.infrastructure.persistence.mapper;

import com.portal.serasa.domain.model.Client;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientEntityMapper {

    Client toDomain(ClientEntity entity);

    @Mapping(target = "updatedAt", ignore = true)
    ClientEntity toEntity(Client domain);
}
