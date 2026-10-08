package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.liberacao.TipoOperacao;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Card como o quadro desenha. As permissões já vêm calculadas para quem pediu. */
@Builder
public record LiberacaoCardResponse(
        UUID id,
        Long numero,
        EtapaLiberacao etapa,
        LocalDateTime etapaDesde,
        Integer rodada,
        String cedenteCnpj,
        String cedenteNome,
        boolean cedenteCadastrado,
        TipoOperacao tipoOperacao,
        BigDecimal valor,
        LocalDateTime prazo,
        String parecerOrigem,
        String criadoPorNome,
        LocalDateTime criadoEm,
        String atualizadoPorNome,
        LocalDateTime atualizadoEm,
        LocalDateTime finalizadoEm,
        Long version,
        int sacadosQtd,
        BigDecimal somaSacados,
        /** Rodada vigente. */
        List<Parecer> pareceres,
        int pendenciasAbertas,
        int comentarios,
        List<Pessoa> membros,
        boolean podeEditar,
        /** Cada etapa de destino possível, com o motivo quando esta pessoa não pode mover. */
        List<Destino> destinos) {

    public record Pessoa(UUID id, String nome, String iniciais) {
    }

    public record Parecer(UUID id, Integer rodada, UUID usuarioId, String usuarioNome, String iniciais,
                          PosicaoParecer posicao, String texto, LocalDateTime registradoEm) {
    }

    public record Destino(EtapaLiberacao etapa, boolean permitido, String motivo) {
    }
}
