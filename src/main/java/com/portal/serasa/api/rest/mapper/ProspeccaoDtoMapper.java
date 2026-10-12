package com.portal.serasa.api.rest.mapper;

import com.portal.serasa.api.rest.dto.DadosComerciaisDto;
import com.portal.serasa.api.rest.dto.response.DocumentoTipoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoArquivoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoDocumentoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoEventoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoResponse;
import com.portal.serasa.application.service.prospeccao.ProspeccaoService;
import com.portal.serasa.infrastructure.persistence.entity.DadosComerciais;
import com.portal.serasa.infrastructure.persistence.entity.DocumentoTipoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoArquivoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEventoEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Entidade para DTO. O SLA é resolvido aqui para a tela não repetir a regra de dias úteis. */
@Component
@RequiredArgsConstructor
public class ProspeccaoDtoMapper {

    private final ProspeccaoService prospeccaoService;

    /**
     * Raiz do compartilhamento como as pessoas veem no Windows, ex.:
     * {@code \\SRVGinfo\FM\Cadastro e Comercial\CLIENTES}. Vazio: o caminho é mostrado a partir
     * de {@code CLIENTES}. O caminho real dentro do container nunca vai para a tela.
     */
    @org.springframework.beans.factory.annotation.Value("${app.documentos.caminho-rede:}")
    private String caminhoRede;

    public ProspeccaoResponse toResponse(ProspeccaoEntity card, String analistaNome,
                                         List<ProspeccaoDocumentoEntity> documentos) {
        return toResponse(card, analistaNome, documentos, null, 0, List.of(), List.of());
    }

    public ProspeccaoResponse toResponse(ProspeccaoEntity card, String analistaNome,
                                         List<ProspeccaoDocumentoEntity> documentos,
                                         java.math.BigDecimal limiteVigente, long pendenciasAbertas,
                                         List<com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity> rodadaVigente,
                                         List<com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEtiquetaEntity> etiquetas) {
        List<com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity> rodada =
                rodadaVigente == null ? List.of() : rodadaVigente;
        long total = documentos == null ? 0 : documentos.stream()
                .filter(doc -> !Boolean.TRUE.equals(doc.getInformativoSnapshot()))
                .filter(doc -> Boolean.TRUE.equals(doc.getSocioAtivo()))
                .count();
        long resolvidos = documentos == null ? 0 : documentos.stream()
                .filter(doc -> !Boolean.TRUE.equals(doc.getInformativoSnapshot()))
                .filter(doc -> Boolean.TRUE.equals(doc.getSocioAtivo()))
                .filter(doc -> doc.getStatus().resolvido())
                .count();

        return ProspeccaoResponse.builder()
                .id(card.getId())
                .cnpj(card.getCnpj())
                .razaoSocial(card.getRazaoSocial())
                .estagio(card.getEstagio())
                .origem(card.getOrigem())
                .comercialId(card.getComercialId())
                .comercialNome(card.getComercialNome())
                .analistaId(card.getAnalistaId())
                .analistaNome(analistaNome)
                .estagioDesde(card.getEstagioDesde())
                .prazoEstagioHoras(card.getPrazoEstagioHoras())
                .horasNoEstagio(prospeccaoService.horasNoEstagio(card))
                .slaEstourado(prospeccaoService.slaEstourado(card))
                .slaEmAtencao(prospeccaoService.slaEmAtencao(card))
                .silencioEmAtencao(prospeccaoService.silencioEmAtencao(card))
                .silencioProlongado(prospeccaoService.silencioProlongado(card))
                .diasEmSilencio(prospeccaoService.diasEmSilencio(card))
                .motivoRecusa(card.getMotivoRecusa())
                .observacao(card.getObservacao())
                .reaberturas(card.getReaberturas())
                .ultimoContatoEm(card.getUltimoContatoEm())
                .documentosResolvidos((int) resolvidos)
                .documentosTotal((int) total)
                .createdAt(card.getCreatedAt())
                .closedAt(card.getClosedAt())
                .legado(Boolean.TRUE.equals(card.getLegado()))
                .limiteVigente(limiteVigente)
                .pendenciasAbertas(pendenciasAbertas)
                .cor(card.getCor())
                .etiquetas((etiquetas == null ? List.<com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEtiquetaEntity>of() : etiquetas)
                        .stream()
                        .sorted(java.util.Comparator.comparing(com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEtiquetaEntity::getNome))
                        .map(etiqueta -> new com.portal.serasa.api.rest.dto.response.ProspeccaoEtiquetaResponse(
                                etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()))
                        .toList())
                .pareceresRegistrados((int) rodada.stream().filter(p -> p.registrado()).count())
                .pareceresEsperados(rodada.size())
                .version(card.getVersion())
                .build();
    }

    public com.portal.serasa.api.rest.dto.response.ProspeccaoDecisaoResponse toResponse(
            com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.DecisaoCompleta completa) {
        var d = completa.decisao();
        return new com.portal.serasa.api.rest.dto.response.ProspeccaoDecisaoResponse(
                d.getId(), d.getVersao(), d.getResultado(), d.getLimite(),
                completa.condicoes().stream()
                        .map(c -> new com.portal.serasa.api.rest.dto.response.ProspeccaoDecisaoResponse.Condicao(c.getProduto(), c.getValor()))
                        .toList(),
                d.getParecer(), d.getMotivoRecusa(), Boolean.TRUE.equals(d.getVigente()),
                d.getDecididoPorNome(), d.getDecididoEm());
    }

    public com.portal.serasa.api.rest.dto.response.ProspeccaoAceiteResponse toResponse(
            com.portal.serasa.infrastructure.persistence.entity.ProspeccaoAceiteEntity a) {
        return new com.portal.serasa.api.rest.dto.response.ProspeccaoAceiteResponse(
                a.getId(), a.getDecisaoId(), a.getResposta(), a.getLimiteSugerido(),
                a.getCondicoesSugeridas() == null ? List.of() : a.getCondicoesSugeridas(),
                a.getMensagem(), a.getRegistradoPorNome(), Boolean.TRUE.equals(a.getEmNomeDoComercial()),
                a.getRegistradoEm());
    }

    public com.portal.serasa.api.rest.dto.response.ProdutoCatalogoResponse toResponse(
            com.portal.serasa.infrastructure.persistence.entity.ProdutoCatalogoEntity p) {
        return new com.portal.serasa.api.rest.dto.response.ProdutoCatalogoResponse(
                p.getId(), p.getNome(), p.getOrdem() == null ? 0 : p.getOrdem(), Boolean.TRUE.equals(p.getAtivo()));
    }

    public com.portal.serasa.api.rest.dto.response.ProspeccaoPendenciaResponse toResponse(
            com.portal.serasa.infrastructure.persistence.entity.ProspeccaoPendenciaEntity p) {
        return new com.portal.serasa.api.rest.dto.response.ProspeccaoPendenciaResponse(
                p.getId(), p.getPergunta(),
                p.getCamposExigidos() == null ? List.of() : p.getCamposExigidos(),
                p.getDocumentosPedidos() == null ? List.of() : p.getDocumentosPedidos(),
                p.getStatus(), p.getAbertaPorNome(), p.getAbertaEm(), p.getResposta(), p.getRespondidaPorNome(),
                Boolean.TRUE.equals(p.getEmNomeDoComercial()), p.getFechadaEm());
    }

    public com.portal.serasa.api.rest.dto.response.ProspeccaoComentarioResponse toResponse(
            com.portal.serasa.infrastructure.persistence.entity.ProspeccaoComentarioEntity c) {
        return new com.portal.serasa.api.rest.dto.response.ProspeccaoComentarioResponse(
                c.getId(), c.getAutorId(), c.getAutorNome(), c.getTexto(), c.getCriadoEm(), c.getEditadoEm());
    }

    public com.portal.serasa.api.rest.dto.response.ProspeccaoParecerResponse toResponse(
            com.portal.serasa.infrastructure.persistence.entity.ProspeccaoParecerEntity p) {
        return new com.portal.serasa.api.rest.dto.response.ProspeccaoParecerResponse(
                p.getId(), p.getRodada(), p.getUsuarioId(), p.getUsuarioNome(), p.getPosicao(), p.getTexto(), p.getRegistradoEm());
    }

    /** Relativo da base → caminho de Windows, com a raiz de rede quando configurada. */
    String caminhoVisivel(String relativo) {
        if (relativo == null) {
            return null;
        }
        String raiz = caminhoRede == null || caminhoRede.isBlank() ? "CLIENTES" : caminhoRede.replaceAll("[\\\\/]+$", "");
        return raiz + "\\" + relativo.replace('/', '\\');
    }

    public DadosComerciaisDto toDto(DadosComerciais dados) {
        DadosComerciais d = dados != null ? dados : new DadosComerciais();
        return new DadosComerciaisDto(
                d.getFaturamentoMensal(), d.getTicketMedio(), d.getValorMedioOperacoes(),
                d.getPrazoMedioVendas(), d.getPrazoMedioPgtoFornecedor(), d.getPrazoMedioEntrega(),
                d.getPossuiCanhotosDias(), d.getOperaMercadoriaEntregue(), d.getOperaMercadoriaEmbarcada(),
                d.getRegiaoAtendimento(), d.getProdutos(), d.getParceirosAtuais(), d.getOutros());
    }

    /** Texto em branco vira nulo: campo apagado na tela não pode ficar gravado como "". */
    public DadosComerciais toEntity(DadosComerciaisDto dto) {
        if (dto == null) {
            return null;
        }
        return DadosComerciais.builder()
                .faturamentoMensal(dto.faturamentoMensal())
                .ticketMedio(dto.ticketMedio())
                .valorMedioOperacoes(dto.valorMedioOperacoes())
                .prazoMedioVendas(dto.prazoMedioVendas())
                .prazoMedioPgtoFornecedor(dto.prazoMedioPgtoFornecedor())
                .prazoMedioEntrega(dto.prazoMedioEntrega())
                .possuiCanhotosDias(dto.possuiCanhotosDias())
                .operaMercadoriaEntregue(dto.operaMercadoriaEntregue())
                .operaMercadoriaEmbarcada(dto.operaMercadoriaEmbarcada())
                .regiaoAtendimento(limpar(dto.regiaoAtendimento()))
                .produtos(limpar(dto.produtos()))
                .parceirosAtuais(limpar(dto.parceirosAtuais()))
                .outros(limpar(dto.outros()))
                .build();
    }

    private static String limpar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    public ProspeccaoDocumentoResponse toResponse(ProspeccaoDocumentoEntity documento,
                                                  Map<UUID, List<ProspeccaoArquivoEntity>> arquivosPorDocumento,
                                                  String cnpj) {
        List<ProspeccaoArquivoResponse> arquivos =
                arquivosPorDocumento.getOrDefault(documento.getId(), List.of()).stream()
                        .map(this::toResponse)
                        .toList();
        return ProspeccaoDocumentoResponse.builder()
                .pasta(cnpj == null ? null : caminhoVisivel(
                        com.portal.serasa.application.service.prospeccao.ProspeccaoArquivoService.pastaDoDocumento(cnpj, documento)))
                .id(documento.getId())
                .codigo(documento.getCodigoSnapshot())
                .nome(documento.getNomeSnapshot())
                .obrigatorio(Boolean.TRUE.equals(documento.getObrigatorioSnapshot()))
                .informativo(Boolean.TRUE.equals(documento.getInformativoSnapshot()))
                .admiteExcecao(Boolean.TRUE.equals(documento.getAdmiteExcecaoSnapshot()))
                .escopo(documento.getEscopo())
                .pessoaPapel(documento.getPessoaPapel() == null ? "SOCIO" : documento.getPessoaPapel().name())
                .socioNome(documento.getSocioNome())
                .socioParticipacao(documento.getSocioParticipacao())
                .socioDocumento(documento.getSocioDocumento())
                .socioAtivo(Boolean.TRUE.equals(documento.getSocioAtivo()))
                .socioInativoMotivo(documento.getSocioInativoMotivo())
                .status(documento.getStatus())
                .observacao(documento.getObservacao())
                .motivo(documento.getMotivo())
                .recebidoEm(documento.getRecebidoEm())
                .validadoEm(documento.getValidadoEm())
                .arquivos(arquivos)
                .build();
    }

    public ProspeccaoArquivoResponse toResponse(ProspeccaoArquivoEntity arquivo) {
        return ProspeccaoArquivoResponse.builder()
                .caminho(caminhoVisivel(arquivo.getCaminhoRelativo()))
                .id(arquivo.getId())
                .documentoId(arquivo.getDocumentoId())
                .nomeOriginal(arquivo.getNomeOriginal())
                .mimeType(arquivo.getMimeType())
                .tamanhoBytes(arquivo.getTamanhoBytes())
                .versao(arquivo.getVersao())
                .enviadoEm(arquivo.getEnviadoEm())
                .build();
    }

    public ProspeccaoEventoResponse toResponse(ProspeccaoEventoEntity evento) {
        return ProspeccaoEventoResponse.builder()
                .id(evento.getId())
                .tipo(evento.getTipo())
                .canal(evento.getCanal())
                .estagioDe(evento.getEstagioDe())
                .estagioPara(evento.getEstagioPara())
                .texto(evento.getTexto())
                .usuarioNome(evento.getUsuarioNome())
                .criadoEm(evento.getCriadoEm())
                .build();
    }

    public DocumentoTipoResponse toResponse(DocumentoTipoEntity tipo) {
        return DocumentoTipoResponse.builder()
                .id(tipo.getId())
                .codigo(tipo.getCodigo())
                .nome(tipo.getNome())
                .escopo(tipo.getEscopo())
                .obrigatorio(Boolean.TRUE.equals(tipo.getObrigatorio()))
                .informativo(Boolean.TRUE.equals(tipo.getInformativo()))
                .admiteExcecao(Boolean.TRUE.equals(tipo.getAdmiteExcecao()))
                .somenteUf(tipo.getSomenteUf())
                .ativo(Boolean.TRUE.equals(tipo.getAtivo()))
                .ordem(tipo.getOrdem())
                .build();
    }
}
