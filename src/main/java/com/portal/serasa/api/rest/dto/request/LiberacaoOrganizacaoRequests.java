package com.portal.serasa.api.rest.dto.request;

import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Corpos das ações de organização do card: etiquetas, cor, membros e relatório. */
public final class LiberacaoOrganizacaoRequests {

    private LiberacaoOrganizacaoRequests() {
    }

    public record Etiqueta(@NotBlank @Size(max = 40) String nome, CorLiberacao cor) {
    }

    /** Conjunto completo de etiquetas do card; lista vazia tira todas. */
    public record EtiquetasDoCard(@NotNull @Size(max = 30) List<UUID> ids) {
    }

    /** Cor nula tira a cor. */
    public record Cor(CorLiberacao cor) {
    }

    public record Membro(@NotNull UUID usuarioId) {
    }

    /** Os cards que a tela mostra depois dos filtros. */
    public record Exportacao(@NotEmpty @Size(max = 5000) List<UUID> ids) {
    }
}
