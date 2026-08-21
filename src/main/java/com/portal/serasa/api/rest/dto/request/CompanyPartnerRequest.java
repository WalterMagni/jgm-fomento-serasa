package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPartnerRequest {

    @NotBlank
    @Size(min = 14, max = 18)
    private String partnerCnpj;

    @Size(max = 2000)
    private String note;
}
