package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDocumentRootRequest {

    @NotBlank(message = "Informe o caminho da pasta")
    @Size(max = 4000, message = "O caminho da pasta é muito longo")
    private String rootPath;
}
