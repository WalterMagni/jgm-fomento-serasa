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
        /** Cidade/UF do cedente, quando o portal sabe. */
        String cedentePraca,
        /** Score da equipe para o cedente (A a D); nulo sem score. */
        com.portal.serasa.domain.model.NotaEmpresa cedenteNota,
        /** Apontamentos da Praça de Pagamento do cedente (Visão Cedente); nulo sem nenhum. */
        Praca pracaPagamento,
        String tipoOperacao,
        /** SEC ou FIDC; nula quando ninguém marcou. */
        com.portal.serasa.domain.model.liberacao.EmpresaOperacao empresa,
        BigDecimal valor,
        /** Taxa da operação, em %. */
        BigDecimal taxa,
        /** TAC, em R$. */
        BigDecimal tac,
        /** Em aberto não confirmado do cedente, em R$. */
        BigDecimal tranche,
        LocalDateTime prazo,
        String parecerOrigem,
        PosicaoParecer posicaoOrigem,
        /** Números da Análise de Risco (AR) importada do PDF; nulo em card digitado à mão. */
        com.portal.serasa.domain.model.liberacao.PropostaAr proposta,
        CorLiberacao cor,
        List<Etiqueta> etiquetas,
        UUID criadoPorId,
        String criadoPorNome,
        LocalDateTime criadoEm,
        String atualizadoPorNome,
        LocalDateTime atualizadoEm,
        /** Quando foi decidido (Aprovado ou Reprovado); nulo antes da decisão. */
        LocalDateTime finalizadoEm,
        /** Finalizado arquivado: só aparece no quadro com o filtro. */
        LocalDateTime arquivadoEm,
        String arquivadoPorNome,
        /** Só em Finalizados: sai das decisões dos sacados. */
        com.portal.serasa.domain.model.liberacao.ResultadoLiberacao resultado,
        /** Ressalva da aprovação; o card ganha a etiqueta "Aprovado c/ ressalva". Nula sem ressalva. */
        String ressalva,
        /** Por que o Comitê mandou para a Diretoria, quem mandou e quando. Nulos fora disso. */
        String motivoDiretoria,
        String enviadoDiretoriaPorNome,
        LocalDateTime enviadoDiretoriaEm,
        /** O cliente declinou: quem registrou, quando e por quê (opcional). Nulos fora disso. */
        LocalDateTime declinadoEm,
        String declinadoPorNome,
        String motivoDeclinio,
        /** Motivos marcados ao reprovar, na ordem do catálogo. Vazio fora de Reprovado. */
        List<String> motivosReprovacao,
        /** Aprovados inteiros mais o valor aprovado dos parciais; nulo sem nenhuma decisão. */
        BigDecimal valorAprovado,
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
        int anexos,
        List<Pessoa> membros,
        boolean podeEditar,
        /** Editar o parecer da origem: quem edita o card ou quem o criou, em qualquer etapa. */
        boolean podeEditarParecerOrigem,
        /** Cada etapa de destino possível, com o motivo quando esta pessoa não pode mover. */
        List<Destino> destinos) {

    public record Pessoa(UUID id, String nome, String iniciais) {
    }

    public record Etiqueta(UUID id, String nome, CorLiberacao cor) {
    }

    public record SacadoCurto(String documento, String nome, String praca) {
    }

    public record Parecer(UUID id, Integer rodada, UUID usuarioId, String usuarioNome, String iniciais,
                          PosicaoParecer posicao, String texto, LocalDateTime registradoEm) {
    }

    /** {@code aviso}: pode mover, mas a tela confirma antes (parecer faltando no Comitê). */
    public record Destino(EtapaLiberacao etapa, boolean permitido, String motivo, String aviso) {
    }

    /** Títulos decididos como CEDENTE na Praça de Pagamento em que o documento aparece, e o valor pago. */
    public record Praca(int titulos, BigDecimal valor) {
    }
}
