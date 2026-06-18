package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceBatchDetailResponse {

    private PaymentPlaceBatchResponse batch;
    private List<PaymentPlaceEntryResponse> entries;
}
