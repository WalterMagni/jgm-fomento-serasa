package com.portal.serasa.infrastructure.integration.cnpja;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.infrastructure.integration.cnpja.dto.CompanyDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class CompanyDetailDtoMapper {

    private final ObjectMapper objectMapper;

    public CompanyDetail toDomain(CompanyDetailDto dto, String rawJson) {
        if (dto == null) {
            return null;
        }

        var company = dto.company();
        var address = dto.address();
        var status = dto.status();

        return CompanyDetail.builder()
                .documentNumber(normalizeCnpj(dto.taxId()))
                .updatedAt(dto.updated())
                .alias(dto.alias())
                .founded(dto.founded())
                .head(dto.head())
                .statusDate(dto.statusDate())
                .statusId(status != null ? status.id() : null)
                .statusText(status != null ? status.text() : null)
                .companyId(company != null ? company.id() : null)
                .companyName(company != null ? company.name() : null)
                .companyEquity(company != null ? company.equity() : null)
                .natureId(company != null && company.nature() != null ? company.nature().id() : null)
                .natureText(company != null && company.nature() != null ? company.nature().text() : null)
                .sizeAcronym(company != null && company.size() != null ? company.size().acronym() : null)
                .sizeText(company != null && company.size() != null ? company.size().text() : null)
                .street(address != null ? address.street() : null)
                .number(address != null ? address.number() : null)
                .details(address != null ? address.details() : null)
                .district(address != null ? address.district() : null)
                .city(address != null ? address.city() : null)
                .state(address != null ? address.state() : null)
                .zip(address != null ? address.zip() : null)
                .countryId(address != null && address.country() != null ? address.country().id() : null)
                .countryName(address != null && address.country() != null ? address.country().name() : null)
                .latitude(address != null ? address.latitude() : null)
                .longitude(address != null ? address.longitude() : null)
                .members(toMaps(dto.company() != null ? dto.company().members() : null))
                .phones(toMaps(dto.phones()))
                .emails(toMaps(dto.emails()))
                .mainActivity(toMap(dto.mainActivity()))
                .sideActivities(toMaps(dto.sideActivities()))
                .rawJson(rawJson)
                .build();
    }

    private String normalizeCnpj(String taxId) {
        if (taxId == null || taxId.isBlank()) {
            return null;
        }
        String digits = taxId.replaceAll("\\D", "");
        return digits.length() >= 14 ? digits.substring(0, 14) : String.format("%14s", digits).replace(' ', '0');
    }

    private List<Map<String, Object>> toMaps(List<?> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(this::toMap)
                .filter(m -> !m.isEmpty())
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        return objectMapper.convertValue(obj, new TypeReference<>() {});
    }
}
