package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Item da busca de empresas já cadastradas (usada para escolher uma parceira). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanySearchItemResponse {

    private String cnpj;
    private String companyName;
    private String alias;
    private String address;
    private String city;
    private String state;
    /** true quando essa empresa já é parceira do CNPJ de referência da busca. */
    private boolean alreadyPartner;
}
