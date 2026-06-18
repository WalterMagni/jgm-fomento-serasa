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
public class CompanyNoteRequest {

    @NotBlank(message = "A anotação não pode ficar em branco")
    @Size(max = 5000, message = "A anotação pode ter no máximo 5000 caracteres")
    private String content;
}
