package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CompanyDetailCreateRequest {

    @NotBlank(message = "CNPJ é obrigatório")
    @Size(min = 14, max = 18)
    @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação")
    private String documentNumber;

    @Size(max = 500)
    private String companyName;
}
