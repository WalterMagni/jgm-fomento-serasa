package com.portal.serasa.api.rest.mapper;

import com.portal.serasa.api.rest.dto.response.CreditAnalysisResponse;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.model.CreditAnalysisStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CreditAnalysisDtoMapper {

    @Mapping(source = "consultaEm", target = "consultaEm")
    @Mapping(target = "status", expression = "java(toStatusString(domain.getStatus()))")
    CreditAnalysisResponse toResponse(CreditAnalysis domain);

    default String toStatusString(CreditAnalysisStatus status) {
        return status != null ? status.name() : null;
    }
}
