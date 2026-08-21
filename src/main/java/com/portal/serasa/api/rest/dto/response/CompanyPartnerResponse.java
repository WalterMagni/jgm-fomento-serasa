package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Empresa parceira vinculada (a "outra ponta" do par) vista a partir de um CNPJ. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPartnerResponse {

    private UUID id;
    private String cnpj;
    private String companyName;
    private String alias;
    private String address;
    private String city;
    private String state;
    private String note;
    private String authorName;
    private LocalDateTime createdAt;
}
