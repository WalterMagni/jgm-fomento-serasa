package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.LiberacaoCardRequest;
import com.portal.serasa.api.rest.dto.request.LiberacaoComentarioRequest;
import com.portal.serasa.api.rest.dto.request.LiberacaoOrganizacaoRequests;
import com.portal.serasa.api.rest.dto.request.LiberacaoParecerRequest;
import com.portal.serasa.api.rest.dto.request.LiberacaoPendenciaRequest;
import com.portal.serasa.api.rest.dto.request.LiberacaoRespostaRequest;
import com.portal.serasa.api.rest.dto.request.LiberacaoTransicaoRequest;
import com.portal.serasa.api.rest.dto.response.LiberacaoCardResponse;
import com.portal.serasa.api.rest.dto.response.LiberacaoDetalheResponse;
import com.portal.serasa.api.rest.mapper.LiberacaoResponseAssembler;
import com.portal.serasa.application.service.liberacao.EmpresaResolver;
import com.portal.serasa.application.service.liberacao.LiberacaoComentarioService;
import com.portal.serasa.application.service.liberacao.LiberacaoExportService;
import com.portal.serasa.application.service.liberacao.LiberacaoOrganizacaoService;
import com.portal.serasa.application.service.liberacao.LiberacaoService;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DadosCard;
import com.portal.serasa.application.service.liberacao.LiberacaoService.DadosSacado;
import com.portal.serasa.application.service.liberacao.LiberacaoService.NovaPendencia;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.security.UsuarioLogado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Esteira de liberação de operações. Design em
 * {@code docs/plans/2026-10-07-esteira-liberacao-design.md}.
 *
 * <p>Toda resposta de escrita devolve o card como o quadro o desenha, já com as permissões de quem
 * pediu — a tela troca o card na lista sem precisar rebuscar o quadro.</p>
 */
@RestController
@RequestMapping("/api/v1/liberacao")
@RequiredArgsConstructor
public class LiberacaoController {

    private final LiberacaoService liberacaoService;
    private final LiberacaoComentarioService comentarioService;
    private final LiberacaoOrganizacaoService organizacaoService;
    private final LiberacaoExportService exportService;
    private final LiberacaoResponseAssembler assembler;
    private final UsuarioLogado usuarioLogado;
    private final EmpresaResolver empresaResolver;
    private final com.portal.serasa.application.service.liberacao.LiberacaoAnexoService anexoService;
    private final com.portal.serasa.application.service.liberacao.PropostaArService propostaService;

    // ---------------------------------------------------------------- leitura

    /** Cards do quadro. Finalizados só a partir de {@code finalizadosDesde} (padrão: 30 dias). */
    @GetMapping
    public ResponseEntity<List<LiberacaoCardResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate finalizadosDesde) {
        UserEntity usuario = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.cards(
                liberacaoService.listarQuadro(finalizadosDesde != null ? finalizadosDesde.atStartOfDay() : null),
                usuario));
    }

    /** Tipos de operação para o formulário e o filtro: padrões e os criados pelo time. */
    @GetMapping("/tipos")
    public ResponseEntity<List<String>> tipos() {
        usuarioLogado.obter();
        return ResponseEntity.ok(liberacaoService.tiposDeOperacao());
    }

    /** {@code historico}: decisões sobre o documento em cards anteriores, a mais recente primeiro. */
    public record EmpresaConhecida(String documento, String nome, String praca, boolean cadastrada,
                                   List<LiberacaoDetalheResponse.DecisaoAnterior> historico) {
    }

    /**
     * O que o portal sabe de cada documento, para o formulário mostrar o sacado assim que o CNPJ
     * termina de ser digitado. Procura no CNPJ Já e nas consultas do Serasa.
     */
    @GetMapping("/empresas")
    public ResponseEntity<List<EmpresaConhecida>> empresas(@RequestParam List<String> documentos) {
        usuarioLogado.obter();
        List<String> limpos = documentos.stream().map(documento -> documento.replaceAll("\\D", "")).distinct().limit(200).toList();
        var conhecidas = empresaResolver.resolver(limpos);
        var historico = liberacaoService.decisoesAnteriores(limpos).stream()
                .collect(java.util.stream.Collectors.groupingBy(LiberacaoService.DecisaoAnterior::documento,
                        java.util.stream.Collectors.mapping(LiberacaoResponseAssembler::anterior, java.util.stream.Collectors.toList())));
        return ResponseEntity.ok(limpos.stream()
                .map(documento -> {
                    var empresa = conhecidas.get(documento);
                    var anteriores = historico.getOrDefault(documento, List.of());
                    return empresa == null
                            ? new EmpresaConhecida(documento, null, null, false, anteriores)
                            : new EmpresaConhecida(documento, empresa.nome(), empresa.praca(), true, anteriores);
                })
                .toList());
    }

    /** O que espera por quem pediu. Alimenta o contador do menu. */
    @GetMapping("/resumo")
    public ResponseEntity<Map<String, Long>> resumo() {
        return ResponseEntity.ok(liberacaoService.resumo(usuarioLogado.obter()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LiberacaoDetalheResponse> detalhe(@PathVariable UUID id) {
        UserEntity usuario = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), usuario));
    }

    // --------------------------------------------------------------- escrita

    @PostMapping
    public ResponseEntity<LiberacaoCardResponse> criar(@Valid @RequestBody LiberacaoCardRequest request) {
        UserEntity autor = usuarioLogado.obter();
        return ResponseEntity.status(201).body(assembler.card(liberacaoService.criar(dados(request), autor), autor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LiberacaoCardResponse> editar(@PathVariable UUID id,
                                                        @Valid @RequestBody LiberacaoCardRequest request) {
        UserEntity autor = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.card(
                liberacaoService.editar(id, request.version(), dados(request), autor), autor));
    }

    @PatchMapping("/{id}/etapa")
    public ResponseEntity<LiberacaoCardResponse> mover(@PathVariable UUID id,
                                                       @Valid @RequestBody LiberacaoTransicaoRequest request) {
        UserEntity autor = usuarioLogado.obter();
        List<NovaPendencia> pendencias = request.pendencias() == null ? List.of()
                : request.pendencias().stream().map(this::pendencia).toList();
        List<LiberacaoService.DecisaoSacado> decisoes = request.decisoes() == null ? List.of()
                : request.decisoes().stream()
                        .map(decisao -> new LiberacaoService.DecisaoSacado(decisao.documento(), decisao.situacao(), decisao.valorAprovado()))
                        .toList();
        return ResponseEntity.ok(assembler.card(liberacaoService.transicionar(
                id, request.de(), request.para(), pendencias, request.observacao(), decisoes, request.resultado(), autor), autor));
    }

    /** Decide um sacado pela lista do card, sem finalizar. Só analista, no Comitê ou em Pendência. */
    @PatchMapping("/{id}/sacados/{documento}/situacao")
    public ResponseEntity<LiberacaoDetalheResponse> decidirSacado(@PathVariable UUID id, @PathVariable String documento,
                                                                  @Valid @RequestBody LiberacaoTransicaoRequest.DecisaoSacado request) {
        UserEntity autor = usuarioLogado.obter();
        liberacaoService.decidirSacado(id, new LiberacaoService.DecisaoSacado(documento, request.situacao(), request.valorAprovado()), autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    /** Parecer de quem pediu, na rodada vigente. Registrar de novo revê o parecer. */
    @PostMapping("/{id}/parecer")
    public ResponseEntity<LiberacaoDetalheResponse> parecer(@PathVariable UUID id,
                                                            @Valid @RequestBody LiberacaoParecerRequest request) {
        UserEntity autor = usuarioLogado.obter();
        var registrado = liberacaoService.registrarParecer(id, request.posicao(), request.texto(), autor);
        return ResponseEntity.ok(assembler.detalhe(registrado.card(), autor));
    }

    @PostMapping("/{id}/pendencias")
    public ResponseEntity<LiberacaoDetalheResponse> novaPendencia(@PathVariable UUID id,
                                                                  @Valid @RequestBody LiberacaoPendenciaRequest request) {
        UserEntity autor = usuarioLogado.obter();
        liberacaoService.novaPendencia(id, pendencia(request), autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    @PatchMapping("/{id}/pendencias/{pendenciaId}/resposta")
    public ResponseEntity<LiberacaoDetalheResponse> responder(@PathVariable UUID id, @PathVariable UUID pendenciaId,
                                                              @Valid @RequestBody LiberacaoRespostaRequest request) {
        UserEntity autor = usuarioLogado.obter();
        liberacaoService.responderPendencia(id, pendenciaId, request.resposta(), autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    // ------------------------------------------------------------ comentários

    @PostMapping("/{id}/comentarios")
    public ResponseEntity<LiberacaoDetalheResponse> comentar(@PathVariable UUID id,
                                                             @Valid @RequestBody LiberacaoComentarioRequest request) {
        UserEntity autor = usuarioLogado.obter();
        comentarioService.comentar(id, request.texto(), autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    @PatchMapping("/{id}/comentarios/{comentarioId}")
    public ResponseEntity<LiberacaoDetalheResponse> editarComentario(@PathVariable UUID id, @PathVariable UUID comentarioId,
                                                                     @Valid @RequestBody LiberacaoComentarioRequest request) {
        UserEntity autor = usuarioLogado.obter();
        comentarioService.editar(id, comentarioId, request.texto(), autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    @DeleteMapping("/{id}/comentarios/{comentarioId}")
    public ResponseEntity<LiberacaoDetalheResponse> apagarComentario(@PathVariable UUID id, @PathVariable UUID comentarioId) {
        UserEntity autor = usuarioLogado.obter();
        comentarioService.apagar(id, comentarioId, autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    // ---------------------------------------------------------------- proposta (AR)

    /**
     * Lê o PDF da Análise de Risco e devolve o que dá para pré-preencher no card. Não grava nada:
     * a proposta vai junto quando o card é salvo, e o PDF entra como anexo depois.
     */
    @PostMapping(value = "/proposta", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<com.portal.serasa.application.service.liberacao.PropostaArService.PropostaImportada> lerProposta(
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        usuarioLogado.obter();
        return ResponseEntity.ok(propostaService.importar(file.getBytes()));
    }

    // ---------------------------------------------------------------- anexos

    @PostMapping(value = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LiberacaoDetalheResponse> anexar(@PathVariable UUID id,
                                                           @org.springframework.web.bind.annotation.RequestPart("file")
                                                           org.springframework.web.multipart.MultipartFile file) {
        UserEntity autor = usuarioLogado.obter();
        anexoService.anexar(id, file, autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    /** Download autenticado. O caminho no compartilhamento nunca chega ao navegador. */
    @GetMapping("/{id}/anexos/{anexoId}")
    public ResponseEntity<byte[]> baixarAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        usuarioLogado.obter();
        var anexo = anexoService.buscar(id, anexoId);
        byte[] conteudo = anexoService.conteudo(anexo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                anexo.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : anexo.getMimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(anexo.getNomeOriginal(), java.nio.charset.StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(conteudo);
    }

    @DeleteMapping("/{id}/anexos/{anexoId}")
    public ResponseEntity<LiberacaoDetalheResponse> removerAnexo(@PathVariable UUID id, @PathVariable UUID anexoId) {
        UserEntity autor = usuarioLogado.obter();
        anexoService.remover(id, anexoId, autor);
        return ResponseEntity.ok(assembler.detalhe(liberacaoService.buscar(id), autor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        liberacaoService.excluir(id, usuarioLogado.obter());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------- etiquetas, cor, membros

    public record EtiquetaResponse(UUID id, String nome, com.portal.serasa.domain.model.liberacao.CorLiberacao cor) {
    }

    @GetMapping("/etiquetas")
    public ResponseEntity<List<EtiquetaResponse>> etiquetas() {
        usuarioLogado.obter();
        return ResponseEntity.ok(organizacaoService.etiquetas().stream()
                .map(etiqueta -> new EtiquetaResponse(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()))
                .toList());
    }

    @PostMapping("/etiquetas")
    public ResponseEntity<EtiquetaResponse> criarEtiqueta(@Valid @RequestBody LiberacaoOrganizacaoRequests.Etiqueta request) {
        var etiqueta = organizacaoService.criarEtiqueta(request.nome(), request.cor(), usuarioLogado.obter());
        return ResponseEntity.ok(new EtiquetaResponse(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()));
    }

    @PatchMapping("/etiquetas/{etiquetaId}")
    public ResponseEntity<EtiquetaResponse> editarEtiqueta(@PathVariable UUID etiquetaId,
                                                           @Valid @RequestBody LiberacaoOrganizacaoRequests.Etiqueta request) {
        var etiqueta = organizacaoService.editarEtiqueta(etiquetaId, request.nome(), request.cor(), usuarioLogado.obter());
        return ResponseEntity.ok(new EtiquetaResponse(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()));
    }

    @DeleteMapping("/etiquetas/{etiquetaId}")
    public ResponseEntity<Void> apagarEtiqueta(@PathVariable UUID etiquetaId) {
        organizacaoService.apagarEtiqueta(etiquetaId, usuarioLogado.obter());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/etiquetas")
    public ResponseEntity<LiberacaoCardResponse> etiquetasDoCard(@PathVariable UUID id,
                                                                 @Valid @RequestBody LiberacaoOrganizacaoRequests.EtiquetasDoCard request) {
        UserEntity autor = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.card(organizacaoService.definirEtiquetas(id, request.ids(), autor), autor));
    }

    @PatchMapping("/{id}/cor")
    public ResponseEntity<LiberacaoCardResponse> cor(@PathVariable UUID id, @RequestBody LiberacaoOrganizacaoRequests.Cor request) {
        UserEntity autor = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.card(organizacaoService.definirCor(id, request.cor(), autor), autor));
    }

    @PostMapping("/{id}/membros")
    public ResponseEntity<LiberacaoCardResponse> adicionarMembro(@PathVariable UUID id,
                                                                 @Valid @RequestBody LiberacaoOrganizacaoRequests.Membro request) {
        UserEntity autor = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.card(organizacaoService.adicionarMembro(id, request.usuarioId(), autor), autor));
    }

    @DeleteMapping("/{id}/membros/{usuarioId}")
    public ResponseEntity<LiberacaoCardResponse> removerMembro(@PathVariable UUID id, @PathVariable UUID usuarioId) {
        UserEntity autor = usuarioLogado.obter();
        return ResponseEntity.ok(assembler.card(organizacaoService.removerMembro(id, usuarioId, autor), autor));
    }

    // ----------------------------------------------------------------- relatório

    /** Planilha dos cards que a tela está mostrando. POST porque a lista de ids pode ser longa. */
    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@Valid @RequestBody LiberacaoOrganizacaoRequests.Exportacao request) {
        usuarioLogado.obter();
        byte[] planilha = exportService.exportar(request.ids());
        String nome = "esteira-liberacao-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(planilha);
    }

    // ---------------------------------------------------------------- internos

    private DadosCard dados(LiberacaoCardRequest request) {
        List<DadosSacado> sacados = request.sacados() == null ? List.of()
                : request.sacados().stream()
                        .map(sacado -> new DadosSacado(sacado.documento(), sacado.nome(), sacado.valor(), sacado.carteira()))
                        .toList();
        return new DadosCard(request.cedenteCnpj(), request.cedenteNome(), request.tipoOperacao(),
                request.valor(), request.prazo(), request.parecerOrigem(), request.posicaoOrigem(), sacados,
                request.proposta());
    }

    private NovaPendencia pendencia(LiberacaoPendenciaRequest request) {
        return new NovaPendencia(request.destinatarioId(), request.texto());
    }
}
