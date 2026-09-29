package com.portal.serasa.api.rest.mapper;

import com.portal.serasa.api.rest.dto.response.DocumentoTipoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoArquivoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoDocumentoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoEventoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoResponse;
import com.portal.serasa.application.service.prospeccao.ProspeccaoService;
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

    public ProspeccaoResponse toResponse(ProspeccaoEntity card, String analistaNome,
                                         List<ProspeccaoDocumentoEntity> documentos) {
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
                .build();
    }

    public ProspeccaoDocumentoResponse toResponse(ProspeccaoDocumentoEntity documento,
                                                  Map<UUID, List<ProspeccaoArquivoEntity>> arquivosPorDocumento) {
        List<ProspeccaoArquivoResponse> arquivos =
                arquivosPorDocumento.getOrDefault(documento.getId(), List.of()).stream()
                        .map(this::toResponse)
                        .toList();
        return ProspeccaoDocumentoResponse.builder()
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
