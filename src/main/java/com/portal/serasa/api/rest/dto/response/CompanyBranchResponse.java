package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyBranchResponse {

    private String cnpj;
    private boolean matriz;
    private boolean inSystem;
    private String nomeFantasia;
    private String uf;
    private String municipio;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
