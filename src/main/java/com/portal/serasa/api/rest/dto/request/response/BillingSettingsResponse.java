package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BillingSettingsResponse {
    private double serasaCostPerQuery;
    private double serasaPfCostPerQuery;
}
