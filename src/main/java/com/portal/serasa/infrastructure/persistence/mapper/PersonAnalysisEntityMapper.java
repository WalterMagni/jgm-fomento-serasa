package com.portal.serasa.infrastructure.persistence.mapper;

import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersonAnalysisEntityMapper {

    PersonAnalysis toDomain(PersonAnalysisEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    PersonAnalysisEntity toEntity(PersonAnalysis domain);
}
