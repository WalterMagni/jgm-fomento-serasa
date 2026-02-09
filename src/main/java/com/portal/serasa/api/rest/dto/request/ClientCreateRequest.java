package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ClientCreateRequest {

    @NotBlank(message = "CNPJ/CPF é obrigatório")
    @Size(min = 14, max = 18)
    @Pattern(regexp = "^[0-9.\\-/]+$", message = "Documento deve conter apenas dígitos ou formatação")
    private String documentNumber;

    @Size(max = 500)
    private String name;

    @Size(max = 255)
    private String email;

    private List<String> phones;
}
