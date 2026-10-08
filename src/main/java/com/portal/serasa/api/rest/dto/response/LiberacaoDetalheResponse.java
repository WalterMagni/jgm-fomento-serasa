package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.TipoEventoLiberacao;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record LiberacaoDetalheResponse(
        LiberacaoCardResponse card,
        List<Sacado> sacados,
        /** Todas as rodadas, a vigente primeiro. */
        List<LiberacaoCardResponse.Parecer> pareceres,
        List<Pendencia> pendencias,
        List<Evento> eventos,
        /** Mais recente primeiro. */
        List<Comentario> comentarios,
        /**
         * CNPJs mencionados em qualquer texto do card, e se cada um tem página de empresa. A tela
         * desenha o chip sólido ou tracejado sem consultar empresa por empresa.
         */
        Map<String, Boolean> empresas) {

    public record Sacado(String documento, String nome, BigDecimal valor, boolean cadastrado) {
    }

    public record Pendencia(UUID id, String abertaPorNome, UUID destinatarioId, String destinatarioNome,
                            String texto, String resposta, LocalDateTime abertaEm,
                            LocalDateTime respondidaEm, String respondidaPorNome, boolean podeResponder) {
    }

    public record Comentario(UUID id, UUID autorId, String autorNome, String iniciais, String texto,
                             LocalDateTime criadoEm, LocalDateTime editadoEm) {
    }

    public record Evento(UUID id, TipoEventoLiberacao tipo, EtapaLiberacao etapaDe, EtapaLiberacao etapaPara,
                         String campo, String valorAntes, String valorDepois, String texto,
                         String usuarioNome, LocalDateTime criadoEm) {
    }
}
