package com.portal.serasa.infrastructure.persistence.mapper;

import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.persistence.entity.CompanyDetailEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CompanyDetailEntityMapper {

    CompanyDetail toDomain(CompanyDetailEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "modifiedAt", ignore = true)
    CompanyDetailEntity toEntity(CompanyDetail domain);
}
