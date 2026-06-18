package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentStorageSettingsResponse {
    private boolean configured;
    private String basePath;
    private String updatedByName;
    private LocalDateTime updatedAt;
}
