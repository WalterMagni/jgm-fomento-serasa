package com.portal.serasa.api.rest.dto.response;

import com.portal.serasa.api.rest.dto.DadosComerciaisDto;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import lombok.Builder;

import java.util.List;

/** Card aberto no painel: identificação, checklist agrupado, timeline e para onde pode ir. */
@Builder
public record ProspeccaoDetalheResponse(
        ProspeccaoResponse card,
        /** Cidade/UF da empresa, quando o portal sabe. */
        String empresaPraca,
        DadosComerciaisDto dadosComerciais,
        List<ProspeccaoDocumentoResponse> documentos,
        List<ProspeccaoEventoResponse> timeline,
        /** Destinos válidos a partir do estágio atual — a tela não repete a máquina de estados. */
        List<EstagioProspeccao> destinosPossiveis,
        /** Todas as decisões, da mais nova para a mais antiga; a vigente vem marcada. */
        List<ProspeccaoDecisaoResponse> decisoes,
        List<ProspeccaoAceiteResponse> aceites,
        /** Da mais nova para a mais antiga. */
        List<ProspeccaoPendenciaResponse> pendencias,
        List<ProspeccaoComentarioResponse> comentarios,
        /** Todas as rodadas, da mais nova para a mais antiga. */
        List<ProspeccaoParecerResponse> pareceres,
        int rodadaComite,
        Contrato contrato,
        /** Quem olha foi convocado na rodada vigente: a tela mostra o formulário de parecer. */
        boolean convocadoNoComite,
        /** Quem olha pode registrar o resultado do comitê: dona, admin ou membro do comitê. */
        boolean podeRegistrarResultado,
        /** Quem está olhando pode decidir: é a analista dona ou admin. Calculado pela mesma regra que autoriza. */
        boolean podeDecidir) {

    public record Contrato(String nome, String telefone, String email) {
    }
}
