package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.api.rest.dto.DadosComerciaisDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Abertura de card. O CNPJ é obrigatório porque é a chave que amarra esteira, análise de
 * crédito, partes ligadas e filiais — e é o nome da pasta no compartilhamento.
 */
public record ProspeccaoCreateRequest(
        @NotBlank @Size(min = 14, max = 18) String cnpj,
        @NotBlank @Size(max = 500) String razaoSocial,
        /** Comercial responsável, quando é usuário do portal. Nulo em cliente direto. */
        UUID comercialId,
        /** Nome do comercial. Preenchido sozinho quando há {@code comercialId}. */
        @Size(max = 200) String comercialNome,
        /** Perfil comercial já conhecido na abertura. Opcional. */
        @Valid DadosComerciaisDto dadosComerciais) {
}
