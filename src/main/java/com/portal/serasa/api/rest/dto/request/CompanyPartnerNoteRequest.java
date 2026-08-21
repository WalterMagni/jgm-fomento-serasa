package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyPartnerNoteRequest {

    @Size(max = 2000)
    private String note;
}
