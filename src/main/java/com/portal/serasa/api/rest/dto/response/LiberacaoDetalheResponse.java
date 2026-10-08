package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.ResultadoLiberacao;
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
        /** Mais recente primeiro. */
        List<Anexo> anexos,
        /**
         * CNPJs mencionados em qualquer texto do card, e se cada um tem página de empresa. A tela
         * desenha o chip sólido ou tracejado sem consultar empresa por empresa.
         */
        Map<String, Boolean> empresas) {

    public record Sacado(String documento, String nome, BigDecimal valor, boolean cadastrado, String praca,
                         ResultadoLiberacao situacao, BigDecimal valorAprovado, String situacaoPorNome,
                         LocalDateTime situacaoEm,
                         /** Decisões sobre o mesmo documento em outros cards, a mais recente primeiro. */
                         List<DecisaoAnterior> historico,
                         /** Linha do sacado na AR, quando o card veio do PDF. */
                         com.portal.serasa.domain.model.liberacao.CarteiraSacado carteira) {
    }

    public record DecisaoAnterior(UUID cardId, Long numero, String cedenteNome, ResultadoLiberacao situacao,
                                  BigDecimal valorAprovado, String decididoPor, LocalDateTime decididoEm) {
    }

    public record Pendencia(UUID id, String abertaPorNome, UUID destinatarioId, String destinatarioNome,
                            String texto, String resposta, LocalDateTime abertaEm,
                            LocalDateTime respondidaEm, String respondidaPorNome, boolean podeResponder) {
    }

    public record Anexo(UUID id, String nome, String mimeType, long tamanhoBytes, String enviadoPorNome,
                        LocalDateTime enviadoEm, boolean podeRemover) {
    }

    public record Comentario(UUID id, UUID autorId, String autorNome, String iniciais, String texto,
                             LocalDateTime criadoEm, LocalDateTime editadoEm) {
    }

    public record Evento(UUID id, TipoEventoLiberacao tipo, EtapaLiberacao etapaDe, EtapaLiberacao etapaPara,
                         String campo, String valorAntes, String valorDepois, String texto,
                         String usuarioNome, LocalDateTime criadoEm) {
    }
}
