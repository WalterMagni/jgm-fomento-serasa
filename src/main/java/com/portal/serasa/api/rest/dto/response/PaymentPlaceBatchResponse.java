package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlaceBatchResponse {

    private UUID id;
    private String fileName;
    private String importedByName;
    private String importedByEmail;
    private LocalDateTime importedAt;
    private String status;
    private int totalEntries;
    private int auditEntries;
    private int unlocatedAgencyEntries;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
