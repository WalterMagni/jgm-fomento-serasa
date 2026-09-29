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
    // A coluna é NOT NULL desde a V17 e o padrão vive no @Builder.Default da entidade — que o
    // mapeamento anulava ao gravar o null do domínio explicitamente. Qualquer origem que não
    // calcule o campo (o seeder, um JSON antigo) derrubava a escrita.
    @Mapping(target = "visaoCedente", source = "visaoCedente", defaultValue = "PENDENTE")
    CreditAnalysisEntity toEntity(CreditAnalysis domain);
}
