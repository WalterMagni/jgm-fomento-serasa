package com.portal.serasa.api.rest.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** As fichas que a tela mostra depois dos filtros. */
public record CommercialInformationExportRequest(@NotEmpty @Size(max = 5000) List<UUID> ids) {
}
