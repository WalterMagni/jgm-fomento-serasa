package com.portal.serasa.infrastructure.integration.cnpja.dto;

/**
 * Resposta da API CNPJ Já contendo o DTO parseado e o JSON bruto para persistência.
 */
public record CnpjApiResponse(CompanyDetailDto dto, String rawJson) {}
