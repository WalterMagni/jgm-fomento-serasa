package com.portal.serasa.api.rest.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class StandardTermResponse {
    private String cnpj;
    private String termText;
    private LocalDateTime updatedAt;
}
