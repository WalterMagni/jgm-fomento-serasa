package com.portal.serasa.infrastructure.integration.serasa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SerasaLoginResponse(
        String accessToken,
        String tokenType,
        String expiresIn,
        List<String> scope
) {}
