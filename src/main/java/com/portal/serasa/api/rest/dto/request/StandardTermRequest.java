package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StandardTermRequest {
    @NotBlank(message = "CNPJ is required")
    private String cnpj;

    @NotBlank(message = "Term text cannot be blank")
    private String termText;
}
