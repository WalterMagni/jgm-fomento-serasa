package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApiUsageLogResponse {
    private String id;
    private String userName;
    private String companyName;
    private String documentNumber;
    private String entityType;
    private String timestamp;
    private String queryType;
    private double cost;
}
