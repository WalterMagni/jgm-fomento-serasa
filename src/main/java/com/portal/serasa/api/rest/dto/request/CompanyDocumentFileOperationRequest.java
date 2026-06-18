package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDocumentFileOperationRequest {

    @Size(max = 4000, message = "O caminho do arquivo é muito longo")
    private String path;

    @Size(max = 255, message = "O novo nome é muito longo")
    private String newName;
}
