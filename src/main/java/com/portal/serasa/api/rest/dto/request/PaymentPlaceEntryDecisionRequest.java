package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceEntryDecisionRequest {

    @NotBlank
    private String decision;

    private String notes;
}
