package com.portal.serasa.infrastructure.persistence.mapper;

import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CreditAnalysisEntityMapper {

    CreditAnalysis toDomain(CreditAnalysisEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "client", ignore = true)
    CreditAnalysisEntity toEntity(CreditAnalysis domain);
}
