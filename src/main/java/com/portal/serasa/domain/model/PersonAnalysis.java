package com.portal.serasa.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonAnalysis {

    private Long id;
    private String cpf;
    private String personName;

    /** reports[0].registration — dados pessoais, endereço, telefone */
    private JsonNode registration;

    /** reports[0].negativeData — pefin, refin, notary, check, collectionRecords */
    private JsonNode negativeSummary;

    /** reports[0].facts — inquiry, judgementFilings, bankrupts, stolenDocuments */
    private JsonNode facts;

    /** reports[0].partner — empresas onde é sócio */
    private JsonNode partnerCompanies;

    private String originalPayload;
    private LocalDateTime consultaEm;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
