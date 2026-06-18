package com.portal.serasa.api.rest.mapper;

import com.portal.serasa.api.rest.dto.response.PersonAnalysisResponse;
import com.portal.serasa.api.rest.dto.response.PersonAnalysisSummaryResponse;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PersonAnalysisDtoMapper {

    PersonAnalysisResponse toResponse(PersonAnalysis domain);

    @Mapping(target = "hasNegative", expression = "java(computeHasNegative(domain))")
    @Mapping(target = "negativeTotalCount", expression = "java(computeNegativeTotalCount(domain))")
    PersonAnalysisSummaryResponse toSummaryResponse(PersonAnalysis domain);

    default boolean computeHasNegative(PersonAnalysis domain) {
        return computeNegativeTotalCount(domain) > 0;
    }

    default int computeNegativeTotalCount(PersonAnalysis domain) {
        JsonNode neg = domain.getNegativeSummary();
        if (neg == null || neg.isNull()) return 0;
        int count = 0;
        count += getSummaryCount(neg, "pefin");
        count += getSummaryCount(neg, "refin");
        count += getSummaryCount(neg, "check");
        count += getSummaryCount(neg, "notary");
        count += getSummaryCount(neg, "collectionRecords");

        JsonNode facts = domain.getFacts();
        if (facts != null && !facts.isNull()) {
            count += getSummaryCount(facts, "judgementFilings");
            count += getSummaryCount(facts, "bankrupts");
        }
        return count;
    }

    private int getSummaryCount(JsonNode parent, String sectionKey) {
        JsonNode section = parent.path(sectionKey);
        if (section.isMissingNode()) return 0;
        JsonNode summary = section.path("summary");
        if (summary.isMissingNode()) return 0;
        return summary.path("count").asInt(0);
    }
}
