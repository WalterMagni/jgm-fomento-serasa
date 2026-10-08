package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
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
        String tipoOperacao,
        BigDecimal valor,
        LocalDateTime prazo,
        String parecerOrigem,
        CorLiberacao cor,
        List<Etiqueta> etiquetas,
        UUID criadoPorId,
        String criadoPorNome,
        LocalDateTime criadoEm,
        String atualizadoPorNome,
        LocalDateTime atualizadoEm,
        LocalDateTime finalizadoEm,
        /** O mais recente entre edição, evento e comentário: base de "ordenar por atividade". */
        LocalDateTime ultimaAtividade,
        Long version,
        int sacadosQtd,
        /** Documento e nome de cada sacado, para a busca do quadro achar o card pelo sacado. */
        List<SacadoCurto> sacados,
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

    public record Etiqueta(UUID id, String nome, CorLiberacao cor) {
    }

    public record SacadoCurto(String documento, String nome) {
    }

    public record Parecer(UUID id, Integer rodada, UUID usuarioId, String usuarioNome, String iniciais,
                          PosicaoParecer posicao, String texto, LocalDateTime registradoEm) {
    }

    public record Destino(EtapaLiberacao etapa, boolean permitido, String motivo) {
    }
}
