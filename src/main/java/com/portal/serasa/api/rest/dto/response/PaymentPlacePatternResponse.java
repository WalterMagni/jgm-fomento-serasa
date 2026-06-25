package com.portal.serasa.api.rest.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Padrão aprendido (par cedente×sacado) para a tela "Padrões aprendidos". */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPlacePatternResponse {

    private UUID id;
    private String clientDocument;
    private String payerDocument;
    private String clientName;
    private String payerName;
    private int cedenteCount;
    private int sacadoCount;
    private int inconclusivoCount;
    private int totalCount;
    private String dominantDecision;   // SACADO | CEDENTE | null (sem dominância)
    private int dominantCount;
    private Integer consistencyPct;    // dominante / total, 0..100
    private String lastDecision;
    private LocalDateTime lastDecidedAt;
    private boolean locked;
    private String lockedDecision;
    private String lockedByName;
}
