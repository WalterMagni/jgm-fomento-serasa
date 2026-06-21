package com.portal.serasa.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Cria (sob demanda, em background) o perfil da empresa do sacado quando um título é
 * decidido como CEDENTE e o sacado ainda não está cadastrado. Roda a busca padrão do
 * CNPJ Já; o Serasa fica para o usuário rodar manualmente depois. Isolado em bean próprio
 * porque o proxy @Async não funciona em auto-invocação (chamado pelo PaymentPlaceAnalysisService).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPlaceSacadoEnricher {

    private final ClientProfileService clientProfileService;

    /**
     * Garante que o CNPJ tenha perfil em company_details e que o grupo (matriz + filiais conhecidas)
     * fique consistente. {@code enrichByCnpja} é idempotente (retorna o existente se já houver) e
     * {@code ensureCompanyGroup} cria a matriz via CNPJ Já quando o sacado é filial e herda a Serasa
     * que a matriz já tiver — sem disparar nova consulta Serasa (dado caro fica manual). Nunca propaga
     * erro: a decisão do analista não pode falhar por CNPJ Já indisponível ou filial sem matriz.
     */
    @Async
    public void ensureProfile(String payerDocument) {
        if (payerDocument == null || payerDocument.isBlank()) {
            return;
        }
        try {
            clientProfileService.enrichByCnpja(payerDocument);
            clientProfileService.ensureCompanyGroup(payerDocument);
        } catch (Exception e) {
            log.warn("Não foi possível criar perfil do sacado {} via CNPJ Já: {}", payerDocument, e.getMessage());
        }
    }
}
