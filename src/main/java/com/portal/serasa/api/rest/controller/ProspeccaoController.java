package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.DadosComerciaisDto;
import com.portal.serasa.api.rest.dto.request.DocumentoTipoRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoCreateRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoDocumentoStatusRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoEventoRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoPessoaRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoSocioAtivoRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoTransicaoRequest;
import com.portal.serasa.api.rest.dto.response.DocumentoTipoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoArquivoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoDetalheResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoDocumentoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoEventoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoResponse;
import com.portal.serasa.api.rest.dto.response.ProspeccaoResumoResponse;
import com.portal.serasa.api.rest.mapper.ProspeccaoDtoMapper;
import com.portal.serasa.application.service.prospeccao.ProspeccaoArquivoService;
import com.portal.serasa.application.service.prospeccao.ProspeccaoAutorizacao;
import com.portal.serasa.application.service.prospeccao.ProspeccaoDocumentoService;
import com.portal.serasa.application.service.prospeccao.ProspeccaoEntradaAutomatica;
import com.portal.serasa.application.service.prospeccao.ProspeccaoExportService;
import com.portal.serasa.application.service.prospeccao.ProspeccaoService;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.infrastructure.persistence.entity.DocumentoTipoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoArquivoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.DocumentoTipoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.format.annotation.DateTimeFormat;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Esteira de prospecção unificada: da abertura até o cedente habilitado.
 *
 * <p>Substitui as abas VISÃO CEDENTE e DOC'S PENDENTES da planilha de controle e absorve o fluxo
 * do Portal Comercial — decisão com limite, aceite do cliente, comitê e contrato. Design em
 * {@code docs/plans/2026-10-11-esteira-unificada-design.md}.</p>
 *
 * <p>Toda pessoa autenticada opera a esteira inteira — ver {@link ProspeccaoAutorizacao} para o
 * porquê. A exceção é o catálogo de documentos, que é configuração e não operação.</p>
 */
@RestController
@RequestMapping("/api/v1/prospeccao")
@RequiredArgsConstructor
public class ProspeccaoController {

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoDocumentoService documentoService;
    private final com.portal.serasa.application.service.prospeccao.ProspeccaoChecklistService checklistService;
    private final ProspeccaoArquivoService arquivoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ProspeccaoEntradaAutomatica entradaAutomatica;
    private final ProspeccaoExportService exportService;
    private final com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService decisaoService;
    private final com.portal.serasa.infrastructure.persistence.repository.ProdutoCatalogoJpaRepository produtoRepository;
    private final com.portal.serasa.application.service.prospeccao.ProspeccaoPendenciaService pendenciaService;
    private final com.portal.serasa.application.service.prospeccao.ProspeccaoComentarioService comentarioService;
    private final com.portal.serasa.application.service.prospeccao.ProspeccaoComiteService comiteService;
    private final com.portal.serasa.application.service.prospeccao.ProspeccaoOrganizacaoService organizacaoService;
    private final ProspeccaoDtoMapper mapper;
    private final DocumentoTipoJpaRepository documentoTipoRepository;
    private final UserRepository userRepository;

    // ---------------------------------------------------------------- leitura

    /** Cards em aberto, ou o histórico de um CNPJ quando ele é informado. */
    @GetMapping
    public ResponseEntity<List<ProspeccaoResponse>> listar(
            @RequestParam(required = false) String cnpj,
            @RequestParam(required = false) EstagioProspeccao estagio,
            @RequestParam(required = false) UUID comercialId,
            @RequestParam(required = false, defaultValue = "false") boolean apenasAtrasados) {

        List<ProspeccaoEntity> cards = cnpj != null && !cnpj.isBlank()
                ? prospeccaoService.listarPorCnpj(cnpj)
                : prospeccaoService.listarVisiveis();

        List<ProspeccaoEntity> filtrados = cards.stream()
                .filter(card -> estagio == null || card.getEstagio() == estagio)
                .filter(card -> comercialId == null || comercialId.equals(card.getComercialId()))
                .toList();

        List<ProspeccaoResponse> resposta = toResponseEmLote(filtrados).stream()
                .filter(card -> !apenasAtrasados || card.slaEstourado() || card.silencioProlongado())
                .toList();
        return ResponseEntity.ok(resposta);
    }

    /** Contadores do topo do kanban. */
    @GetMapping("/resumo")
    public ResponseEntity<ProspeccaoResumoResponse> resumo() {
        List<ProspeccaoResponse> abertos = toResponseEmLote(prospeccaoService.listarAbertas());

        Map<String, Long> porEstagio = new LinkedHashMap<>();
        for (EstagioProspeccao valor : EstagioProspeccao.values()) {
            porEstagio.put(valor.name(),
                    abertos.stream().filter(card -> card.estagio() == valor).count());
        }

        return ResponseEntity.ok(ProspeccaoResumoResponse.builder()
                .porEstagio(porEstagio)
                .total(abertos.size())
                .slaEstourado(abertos.stream().filter(ProspeccaoResponse::slaEstourado).count())
                .slaEmAtencao(abertos.stream()
                        .filter(card -> card.slaEmAtencao() || card.silencioEmAtencao())
                        .count())
                .silencioProlongado(abertos.stream().filter(ProspeccaoResponse::silencioProlongado).count())
                .precisamAtencao(abertos.stream()
                        .filter(card -> card.slaEstourado() || card.silencioProlongado())
                        .count())
                .aguardandoComercial(abertos.stream().filter(card -> card.pendenciasAbertas() > 0).count())
                .pareceresAguardandoMeus(comiteService.cardsAguardandoParecerDe(usuarioAutenticado()))
                .build());
    }

    /**
     * Mapa de estágio para destinos válidos.
     *
     * <p>Existe para o kanban validar o arraste sem reimplementar a máquina de estados: duas
     * cópias da regra divergiriam, e o usuário descobriria a divergência soltando o card e
     * tomando 409.</p>
     */
    @GetMapping("/estagios")
    public ResponseEntity<Map<String, List<String>>> estagios() {
        Map<String, List<String>> destinos = new LinkedHashMap<>();
        for (EstagioProspeccao estagio : EstagioProspeccao.values()) {
            destinos.put(estagio.name(), estagio.destinos().stream()
                    .map(EstagioProspeccao::name)
                    .sorted()
                    .toList());
        }
        return ResponseEntity.ok(destinos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProspeccaoDetalheResponse> detalhe(@PathVariable UUID id) {
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        UserEntity eu = usuarioAutenticado();
        var pareceres = comiteService.pareceres(id);
        boolean convocado = card.getEstagio() == EstagioProspeccao.COMITE && eu != null && pareceres.stream()
                .anyMatch(p -> p.getRodada().equals(card.getRodadaComite()) && eu.getId().equals(p.getUsuarioId()));
        return ResponseEntity.ok(ProspeccaoDetalheResponse.builder()
                .card(toResponse(card))
                .dadosComerciais(mapper.toDto(card.getDadosComerciais()))
                .documentos(documentos(id))
                .timeline(prospeccaoService.timeline(id).stream().map(mapper::toResponse).toList())
                .destinosPossiveis(card.getEstagio().destinos().stream()
                        .sorted(Comparator.comparing(EstagioProspeccao::name))
                        .toList())
                .decisoes(decisaoService.historico(id).stream().map(mapper::toResponse).toList())
                .aceites(decisaoService.aceites(id).stream().map(mapper::toResponse).toList())
                .pendencias(pendenciaService.listar(id).stream().map(mapper::toResponse).toList())
                .comentarios(comentarioService.listar(id).stream().map(mapper::toResponse).toList())
                .pareceres(pareceres.stream().map(mapper::toResponse).toList())
                .rodadaComite(card.getRodadaComite() == null ? 0 : card.getRodadaComite())
                .contrato(new ProspeccaoDetalheResponse.Contrato(card.getContratoContatoNome(),
                        card.getContratoContatoTelefone(), card.getContratoContatoEmail()))
                .convocadoNoComite(convocado)
                .podeRegistrarResultado(autorizacao.ehDona(card, eu) || (eu != null && eu.isComite()))
                .podeDecidir(autorizacao.ehDona(card, eu))
                .build());
    }

    // --------------------------------------------------------------- escrita

    @PostMapping
    public ResponseEntity<ProspeccaoResponse> criar(@Valid @RequestBody ProspeccaoCreateRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirCriador(autor);

        // O comercial é opcional: quem abre é a equipe interna, e cliente direto não tem
        // intermediário. Com id, o nome vem do cadastro; sem id, vale o nome digitado.
        UUID comercialId = request.comercialId();
        String comercialNome = request.comercialNome() != null && !request.comercialNome().isBlank()
                ? request.comercialNome().trim()
                : comercialId != null ? nomeDoUsuario(comercialId) : null;

        ProspeccaoEntity card = prospeccaoService.criar(
                request.cnpj(), request.razaoSocial(), comercialId, comercialNome,
                mapper.toEntity(request.dadosComerciais()), autor);
        return ResponseEntity.status(201).body(toResponse(card));
    }

    /**
     * Decisão da análise: aprova com limite e condições, ou reprova com motivo. Só a analista dona
     * do card (ou o admin) decide.
     */
    @PostMapping("/{id}/decisao")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoDecisaoResponse> decidir(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoDecisaoRequest request) {
        var condicoes = request.condicoes() == null ? List.<com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.Condicao>of()
                : request.condicoes().stream()
                        .map(c -> new com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.Condicao(c.produto(), c.valor()))
                        .toList();
        var decisao = decisaoService.decidir(id,
                new com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.ComandoDecisao(
                        request.resultado(), request.limite(), condicoes, request.parecer(),
                        request.motivoRecusa(), request.observacao()),
                usuarioAutenticado());
        return ResponseEntity.status(201).body(mapper.toResponse(decisao));
    }

    /** Resposta do cliente à proposta vigente: aceite, recusa ou contraproposta. */
    @PostMapping("/{id}/aceite")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoAceiteResponse> registrarAceite(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoAceiteRequest request) {
        var aceite = decisaoService.registrarAceite(id,
                new com.portal.serasa.application.service.prospeccao.ProspeccaoDecisaoService.ComandoAceite(
                        request.resposta(), request.mensagem(), request.limiteSugerido(), request.condicoesSugeridas()),
                usuarioAutenticado());
        return ResponseEntity.status(201).body(mapper.toResponse(aceite));
    }

    // ------------------------------------------------------------- pendências

    /** Pedido ao comercial. Só a analista dona abre; o prazo do estágio para até a resposta. */
    @PostMapping("/{id}/pendencias")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoPendenciaResponse> abrirPendencia(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoPendenciaRequest request) {
        var pendencia = pendenciaService.abrir(id, request.pergunta(), request.camposExigidos(),
                request.documentosPedidos(), usuarioAutenticado());
        return ResponseEntity.status(201).body(mapper.toResponse(pendencia));
    }

    @PatchMapping("/pendencias/{pendenciaId}/resposta")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoPendenciaResponse> responderPendencia(
            @PathVariable UUID pendenciaId,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoTextoRequest request) {
        return ResponseEntity.ok(mapper.toResponse(pendenciaService.responder(pendenciaId, request.texto(), usuarioAutenticado())));
    }

    @PatchMapping("/pendencias/{pendenciaId}/cancelar")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoPendenciaResponse> cancelarPendencia(
            @PathVariable UUID pendenciaId) {
        return ResponseEntity.ok(mapper.toResponse(pendenciaService.cancelar(pendenciaId, usuarioAutenticado())));
    }

    // ------------------------------------------------------- comitê e contrato

    /** Leva ao comitê numa rodada nova e convoca os membros. Só a dona do card. */
    @PostMapping("/{id}/comite")
    public ResponseEntity<ProspeccaoResponse> enviarAoComite(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(comiteService.enviar(id, usuarioAutenticado())));
    }

    @PatchMapping("/{id}/parecer")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoParecerResponse> registrarParecer(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoParecerRequest request) {
        var registrado = comiteService.registrarParecer(id, request.posicao(), request.texto(), usuarioAutenticado());
        return ResponseEntity.ok(mapper.toResponse(registrado.parecer()));
    }

    @PostMapping("/{id}/comite/resultado")
    public ResponseEntity<ProspeccaoResponse> resultadoDoComite(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoResultadoComiteRequest request) {
        comiteService.registrarResultado(id, request.resultado(), request.motivoRecusa(), request.observacao(), usuarioAutenticado());
        return ResponseEntity.ok(toResponse(prospeccaoService.buscar(id)));
    }

    @PutMapping("/{id}/contrato")
    public ResponseEntity<ProspeccaoDetalheResponse.Contrato> salvarContrato(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoContratoRequest request) {
        ProspeccaoEntity card = comiteService.salvarContrato(id, request.nome(), request.telefone(), request.email(), usuarioAutenticado());
        return ResponseEntity.ok(new ProspeccaoDetalheResponse.Contrato(card.getContratoContatoNome(),
                card.getContratoContatoTelefone(), card.getContratoContatoEmail()));
    }

    /** Conclui em HABILITADO. Exige o contato do contrato. Só a dona do card. */
    @PostMapping("/{id}/concluir")
    public ResponseEntity<ProspeccaoResponse> concluir(@PathVariable UUID id) {
        return ResponseEntity.ok(toResponse(comiteService.concluir(id, usuarioAutenticado())));
    }

    // ------------------------------------------------------- cor e etiquetas

    @GetMapping("/etiquetas")
    public ResponseEntity<List<com.portal.serasa.api.rest.dto.response.ProspeccaoEtiquetaResponse>> etiquetas() {
        return ResponseEntity.ok(organizacaoService.etiquetas().stream()
                .map(e -> new com.portal.serasa.api.rest.dto.response.ProspeccaoEtiquetaResponse(e.getId(), e.getNome(), e.getCor()))
                .toList());
    }

    /** Criar com nome existente devolve a existente. */
    @PostMapping("/etiquetas")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoEtiquetaResponse> criarEtiqueta(
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoEtiquetaRequest request) {
        var e = organizacaoService.criarEtiqueta(request.nome(), request.cor(), usuarioAutenticado());
        return ResponseEntity.ok(new com.portal.serasa.api.rest.dto.response.ProspeccaoEtiquetaResponse(e.getId(), e.getNome(), e.getCor()));
    }

    @DeleteMapping("/etiquetas/{etiquetaId}")
    public ResponseEntity<Void> apagarEtiqueta(@PathVariable UUID etiquetaId) {
        organizacaoService.apagarEtiqueta(etiquetaId, usuarioAutenticado());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/etiquetas")
    public ResponseEntity<Void> definirEtiquetas(@PathVariable UUID id, @RequestBody Map<String, List<UUID>> corpo) {
        organizacaoService.definirEtiquetas(id, corpo.get("ids"), usuarioAutenticado());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/cor")
    public ResponseEntity<Void> definirCor(@PathVariable UUID id, @RequestBody Map<String, String> corpo) {
        String cor = corpo.get("cor");
        organizacaoService.definirCor(id,
                cor == null || cor.isBlank() ? null : com.portal.serasa.domain.model.liberacao.CorLiberacao.valueOf(cor),
                usuarioAutenticado());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ comentários

    @PostMapping("/{id}/comentarios")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoComentarioResponse> comentar(
            @PathVariable UUID id,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoTextoRequest request) {
        return ResponseEntity.status(201).body(mapper.toResponse(comentarioService.comentar(id, request.texto(), usuarioAutenticado())));
    }

    @PutMapping("/{id}/comentarios/{comentarioId}")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProspeccaoComentarioResponse> editarComentario(
            @PathVariable UUID id,
            @PathVariable UUID comentarioId,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProspeccaoTextoRequest request) {
        return ResponseEntity.ok(mapper.toResponse(comentarioService.editar(id, comentarioId, request.texto(), usuarioAutenticado())));
    }

    @DeleteMapping("/{id}/comentarios/{comentarioId}")
    public ResponseEntity<Void> apagarComentario(@PathVariable UUID id, @PathVariable UUID comentarioId) {
        comentarioService.apagar(id, comentarioId, usuarioAutenticado());
        return ResponseEntity.noContent().build();
    }

    // --------------------------------------------------- catálogo de produtos

    /** Produtos das condições de crédito. Todos leem; só o admin altera. */
    @GetMapping("/produtos")
    public ResponseEntity<List<com.portal.serasa.api.rest.dto.response.ProdutoCatalogoResponse>> produtos() {
        return ResponseEntity.ok(produtoRepository.findAllByOrderByOrdemAscNomeAsc().stream()
                .map(mapper::toResponse)
                .toList());
    }

    @PostMapping("/produtos")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProdutoCatalogoResponse> criarProduto(
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProdutoCatalogoRequest request) {
        autorizacao.exigirAdmin(usuarioAutenticado());
        String nome = request.nome().trim().toUpperCase(java.util.Locale.ROOT);
        if (produtoRepository.existsByNomeIgnoreCase(nome)) {
            throw new com.portal.serasa.domain.exception.TransicaoInvalidaException("Produto já existe no catálogo: " + nome);
        }
        var produto = produtoRepository.save(com.portal.serasa.infrastructure.persistence.entity.ProdutoCatalogoEntity.builder()
                .nome(nome)
                .ordem(request.ordem() == null ? 99 : request.ordem())
                .ativo(request.ativo() == null || request.ativo())
                .createdAt(LocalDateTime.now())
                .build());
        return ResponseEntity.status(201).body(mapper.toResponse(produto));
    }

    @PatchMapping("/produtos/{produtoId}")
    public ResponseEntity<com.portal.serasa.api.rest.dto.response.ProdutoCatalogoResponse> atualizarProduto(
            @PathVariable UUID produtoId,
            @Valid @RequestBody com.portal.serasa.api.rest.dto.request.ProdutoCatalogoRequest request) {
        autorizacao.exigirAdmin(usuarioAutenticado());
        var produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado no catálogo"));
        produto.setNome(request.nome().trim().toUpperCase(java.util.Locale.ROOT));
        if (request.ordem() != null) {
            produto.setOrdem(request.ordem());
        }
        if (request.ativo() != null) {
            produto.setAtivo(request.ativo());
        }
        return ResponseEntity.ok(mapper.toResponse(produtoRepository.save(produto)));
    }

    /** Substitui o perfil comercial do card pelo formulário enviado. */
    @PutMapping("/{id}/dados-comerciais")
    public ResponseEntity<DadosComerciaisDto> atualizarDadosComerciais(
            @PathVariable UUID id,
            @Valid @RequestBody DadosComerciaisDto request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirEscrita(prospeccaoService.buscar(id), autor);
        ProspeccaoEntity card = prospeccaoService.atualizarDadosComerciais(id, mapper.toEntity(request), autor);
        return ResponseEntity.ok(mapper.toDto(card.getDadosComerciais()));
    }

    /** O analista puxa o card da fila. Quem chegar primeiro fica com ele. */
    @PatchMapping("/{id}/assumir")
    public ResponseEntity<ProspeccaoResponse> assumir(@PathVariable UUID id) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirDecisor(autor);
        return ResponseEntity.ok(toResponse(prospeccaoService.assumir(id, autor)));
    }

    /**
     * Move o card de estágio.
     *
     * <p>Quando a documentação não está completa, a resposta é 422 com a lista do que falta, e é
     * ela que a tela mostra ao devolver o card para a coluna de origem.</p>
     */
    @PatchMapping("/{id}/estagio")
    public ResponseEntity<ProspeccaoResponse> transicionar(
            @PathVariable UUID id,
            @Valid @RequestBody ProspeccaoTransicaoRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirEscrita(prospeccaoService.buscar(id), autor);

        return ResponseEntity.ok(toResponse(prospeccaoService.transicionar(
                id, request.estagio(), request.motivoRecusa(), request.observacao(), autor)));
    }

    @PatchMapping("/{id}/reabrir")
    public ResponseEntity<ProspeccaoResponse> reabrir(
            @PathVariable UUID id,
            @Valid @RequestBody ProspeccaoEventoRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirDecisor(autor);
        return ResponseEntity.ok(toResponse(prospeccaoService.reabrir(id, request.texto(), autor)));
    }

    /** Apaga um card. Ver {@link ProspeccaoService#excluir} para o que some junto. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirExclusao(autor);
        if (prospeccaoService.excluir(List.of(id), autor) == 0) {
            throw new com.portal.serasa.domain.exception.EntityNotFoundException(
                    "Card de prospecção não encontrado: " + id);
        }
        return ResponseEntity.noContent().build();
    }

    /** Apaga em lote. POST e não DELETE porque corpo em DELETE é ignorado por alguns proxies. */
    @PostMapping("/excluir")
    public ResponseEntity<Map<String, Object>> excluirEmLote(@RequestBody Map<String, List<UUID>> corpo) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirExclusao(autor);
        return ResponseEntity.ok(Map.of("cardsApagados", prospeccaoService.excluir(corpo.get("ids"), autor)));
    }

    /** Cobrança quando há canal; nota interna quando não há. */
    @PostMapping("/{id}/eventos")
    public ResponseEntity<ProspeccaoEventoResponse> registrarEvento(
            @PathVariable UUID id,
            @Valid @RequestBody ProspeccaoEventoRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirEscrita(prospeccaoService.buscar(id), autor);

        var evento = request.canal() == null
                ? prospeccaoService.registrarNota(id, request.texto(), autor)
                : prospeccaoService.registrarCobranca(id, request.canal(), request.texto(), autor);
        return ResponseEntity.status(201).body(mapper.toResponse(evento));
    }

    // ------------------------------------------------------------ documentos

    @GetMapping("/{id}/documentos")
    public ResponseEntity<List<ProspeccaoDocumentoResponse>> documentosDoCard(@PathVariable UUID id) {
        return ResponseEntity.ok(documentos(id));
    }

    @PatchMapping("/documentos/{documentoId}")
    public ResponseEntity<ProspeccaoDocumentoResponse> atualizarDocumento(
            @PathVariable UUID documentoId,
            @Valid @RequestBody ProspeccaoDocumentoStatusRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirConferente(autor);

        ProspeccaoDocumentoEntity documento = documentoService.atualizarStatus(
                documentoId, request.status(), request.observacao(), request.motivo(), autor);
        return ResponseEntity.ok(mapper.toResponse(documento, arquivosPorDocumento(documento.getProspeccaoId())));
    }

    /**
     * Adiciona ao checklist uma pessoa que não está no quadro societário — o avalista.
     *
     * <p>Ela recebe os mesmos itens do sócio. Não há de onde puxá-la automaticamente: avalista não
     * consta da Receita nem do Serasa como sócio.</p>
     */
    @PostMapping("/{id}/pessoas")
    public ResponseEntity<List<ProspeccaoDocumentoResponse>> adicionarPessoa(
            @PathVariable UUID id,
            @Valid @RequestBody ProspeccaoPessoaRequest request) {
        UserEntity autor = usuarioAutenticado();
        ProspeccaoEntity card = prospeccaoService.buscar(id);
        autorizacao.exigirConferente(autor);

        checklistService.adicionarPessoa(card, request.nome(), request.documento(), request.papel());
        prospeccaoService.registrarEvento(card,
                com.portal.serasa.domain.model.prospeccao.TipoEventoProspeccao.NOTA, null, null, null,
                "%s adicionado ao checklist: %s".formatted(
                        request.papel() == com.portal.serasa.domain.model.prospeccao.PapelPessoa.AVALISTA
                                ? "Avalista" : "Sócio",
                        request.nome()),
                autor);
        return ResponseEntity.status(201).body(documentos(id));
    }

    /** Tira ou devolve um sócio ao checklist, sem apagar o histórico dele. */
    @PatchMapping("/{id}/socios")
    public ResponseEntity<Map<String, Object>> definirSocio(
            @PathVariable UUID id,
            @Valid @RequestBody ProspeccaoSocioAtivoRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirConferente(autor);

        int itens = documentoService.definirSocioAtivo(
                id, request.socioNome(), request.ativo(), request.motivo(), autor);
        return ResponseEntity.ok(Map.of("itensAtualizados", itens));
    }

    // --------------------------------------------------------------- arquivos

    @PostMapping(value = "/{id}/documentos/{documentoId}/arquivo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProspeccaoArquivoResponse> enviarArquivo(
            @PathVariable UUID id,
            @PathVariable UUID documentoId,
            @RequestPart("file") MultipartFile file) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirEscrita(prospeccaoService.buscar(id), autor);

        ProspeccaoArquivoEntity arquivo = arquivoService.enviar(id, documentoId, file, autor);
        return ResponseEntity.status(201).body(mapper.toResponse(arquivo));
    }

    /** Download autenticado. O caminho no compartilhamento nunca chega ao cliente. */
    @GetMapping("/arquivos/{arquivoId}")
    public ResponseEntity<byte[]> baixarArquivo(@PathVariable UUID arquivoId) {
        usuarioAutenticado();
        ProspeccaoArquivoEntity arquivo = arquivoService.buscar(arquivoId);
        byte[] conteudo = arquivoService.conteudo(arquivoId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                arquivo.getMimeType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : arquivo.getMimeType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(arquivo.getNomeOriginal(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(conteudo);
    }

    /**
     * Todos os documentos do card num ZIP: a versão mais recente de cada item, com o nome do
     * documento, em pastas por empresa e por pessoa.
     *
     * <p>O plano é resolvido antes de responder — se não há arquivo nenhum, sai 404 com mensagem,
     * e não um ZIP vazio. O ZIP em si é escrito direto na resposta, arquivo por arquivo, sem montar
     * tudo em memória.</p>
     */
    @GetMapping("/{id}/arquivos/zip")
    public ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> baixarZip(
            @PathVariable UUID id) {
        usuarioAutenticado();
        var plano = arquivoService.planoDoZip(id);

        org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody corpo = saida -> {
            try (var zip = new java.util.zip.ZipOutputStream(saida, StandardCharsets.UTF_8)) {
                for (var item : plano.itens()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(item.caminhoNoZip()));
                    java.nio.file.Files.copy(item.origem(), zip);
                    zip.closeEntry();
                }
                if (!plano.faltando().isEmpty()) {
                    zip.putNextEntry(new java.util.zip.ZipEntry("LEIA-ME - arquivos que faltaram.txt"));
                    String texto = "Estes documentos têm registro na esteira, mas o arquivo não foi encontrado no\r\n"
                            + "compartilhamento (movido ou renomeado fora do portal):\r\n\r\n- "
                            + String.join("\r\n- ", plano.faltando()) + "\r\n";
                    zip.write(texto.getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(plano.nomeDoArquivo(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(corpo);
    }

    @DeleteMapping("/arquivos/{arquivoId}")
    public ResponseEntity<Void> removerArquivo(@PathVariable UUID arquivoId) {
        UserEntity autor = usuarioAutenticado();
        ProspeccaoArquivoEntity arquivo = arquivoService.buscar(arquivoId);
        autorizacao.exigirEscrita(prospeccaoService.buscar(arquivo.getProspeccaoId()), autor);

        arquivoService.remover(arquivoId, autor);
        return ResponseEntity.noContent().build();
    }

    /**
     * Exporta a esteira em CSV, um assunto por arquivo.
     *
     * <p>Os filtros são os mesmos da listagem, então o que sai é o que a pessoa está vendo. O
     * arquivo abre no Excel com as colunas separadas e os acentos certos, sem depender de nenhuma
     * biblioteca nova.</p>
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(defaultValue = "CARDS") ProspeccaoExportService.Conteudo tipo,
            @RequestParam(required = false) EstagioProspeccao estagio,
            @RequestParam(required = false) UUID comercialId,
            @RequestParam(required = false, defaultValue = "false") boolean apenasAtrasados) {
        usuarioAutenticado();

        List<ProspeccaoEntity> cards = prospeccaoService.listarVisiveis().stream()
                .filter(card -> estagio == null || card.getEstagio() == estagio)
                .filter(card -> comercialId == null || comercialId.equals(card.getComercialId()))
                .filter(card -> !apenasAtrasados
                        || prospeccaoService.slaEstourado(card)
                        || prospeccaoService.silencioProlongado(card))
                .toList();

        byte[] conteudo = exportService.exportar(tipo, cards).getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(exportService.nomeDoArquivo(tipo), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(conteudo);
    }

    /** Quantas empresas o backfill traria, sem criar nada. A tela pergunta antes de agir. */
    @GetMapping("/backfill-visao-cedente/previa")
    public ResponseEntity<ProspeccaoEntradaAutomatica.Previa> previaBackfill(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        usuarioAutenticado();
        return ResponseEntity.ok(entradaAutomatica.previa(desde));
    }

    /**
     * Empresas que o backfill traria, com busca por nome ou CNPJ.
     *
     * <p>É o caminho para trazer uma empresa específica em vez do lote inteiro — em produção são
     * centenas de análises com visão cedente, e quase sempre o que se quer é uma delas.</p>
     */
    @GetMapping("/backfill-visao-cedente/candidatas")
    public ResponseEntity<ProspeccaoEntradaAutomatica.Candidatas> candidatasBackfill(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false, defaultValue = "false") boolean incluirJaNaEsteira) {
        usuarioAutenticado();
        return ResponseEntity.ok(entradaAutomatica.buscarCandidatas(busca, desde, incluirJaNaEsteira));
    }

    /** Traz para a triagem apenas as empresas escolhidas na lista. */
    @PostMapping("/backfill-visao-cedente/escolhidas")
    public ResponseEntity<Map<String, Object>> trazerEscolhidas(@RequestBody Map<String, List<String>> corpo) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirDecisor(autor);
        return ResponseEntity.ok(Map.of(
                "cardsCriados", entradaAutomatica.trazerEscolhidas(corpo.get("cnpjs"))));
    }

    /**
     * Puxa para a esteira as análises com visão cedente SIM que já existiam antes desta tela.
     *
     * <p>É botão, não job de deploy: a analista decide se quer a fila cheia de histórico no
     * primeiro dia. Reexecutar é seguro, quem já tem card é ignorado.</p>
     *
     * <p>O lote tem teto. Em produção são centenas de análises com visão cedente SIM, e sem
     * limite um clique despejaria todas em triagem de uma vez, sem desfazer em massa.</p>
     */
    @PostMapping("/backfill-visao-cedente")
    public ResponseEntity<Map<String, Object>> backfillVisaoCedente(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false, defaultValue = "0") int limite) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirDecisor(autor);
        return ResponseEntity.ok(Map.of("cardsCriados", entradaAutomatica.backfill(desde, limite)));
    }

    // -------------------------------------------------- catálogo (só admin)

    @GetMapping("/documento-tipos")
    public ResponseEntity<List<DocumentoTipoResponse>> listarTipos() {
        return ResponseEntity.ok(documentoTipoRepository.findByAtivoTrueOrderByEscopoAscOrdemAsc()
                .stream().map(mapper::toResponse).toList());
    }

    @PostMapping("/documento-tipos")
    public ResponseEntity<DocumentoTipoResponse> criarTipo(@Valid @RequestBody DocumentoTipoRequest request) {
        autorizacao.exigirAdmin(usuarioAutenticado());
        documentoTipoRepository.findByCodigo(request.codigo()).ifPresent(existente -> {
            throw new IllegalArgumentException("Já existe documento com o código " + request.codigo());
        });

        LocalDateTime agora = LocalDateTime.now();
        DocumentoTipoEntity tipo = documentoTipoRepository.save(DocumentoTipoEntity.builder()
                .codigo(request.codigo())
                .nome(request.nome())
                .escopo(request.escopo())
                .obrigatorio(request.obrigatorio())
                .informativo(request.informativo())
                .admiteExcecao(request.admiteExcecao())
                .somenteUf(request.somenteUf())
                .ativo(request.ativo())
                .ordem(request.ordem())
                .createdAt(agora)
                .updatedAt(agora)
                .build());
        return ResponseEntity.status(201).body(mapper.toResponse(tipo));
    }

    /**
     * Altera um item do catálogo.
     *
     * <p>Não toca em card nenhum: cada card guarda cópia do que valia quando o checklist foi
     * criado, então mexer aqui muda o que passa a ser exigido dali em diante.</p>
     */
    @PutMapping("/documento-tipos/{tipoId}")
    public ResponseEntity<DocumentoTipoResponse> atualizarTipo(
            @PathVariable UUID tipoId,
            @Valid @RequestBody DocumentoTipoRequest request) {
        autorizacao.exigirAdmin(usuarioAutenticado());

        DocumentoTipoEntity tipo = documentoTipoRepository.findById(tipoId)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado no catálogo"));
        tipo.setNome(request.nome());
        tipo.setEscopo(request.escopo());
        tipo.setObrigatorio(request.obrigatorio());
        tipo.setInformativo(request.informativo());
        tipo.setAdmiteExcecao(request.admiteExcecao());
        tipo.setSomenteUf(request.somenteUf());
        tipo.setAtivo(request.ativo());
        tipo.setOrdem(request.ordem());
        tipo.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(mapper.toResponse(documentoTipoRepository.save(tipo)));
    }

    // -------------------------------------------------------------- internals

    private ProspeccaoResponse toResponse(ProspeccaoEntity card) {
        return mapper.toResponse(card, nomeDoUsuario(card.getAnalistaId()),
                documentoService.listar(card.getId()),
                decisaoService.limitesVigentes(List.of(card.getId())).get(card.getId()),
                pendenciaService.abertasPorCard(List.of(card.getId())).getOrDefault(card.getId(), 0L),
                comiteService.rodadaVigente(List.of(card.getId())).getOrDefault(card.getId(), List.of()),
                organizacaoService.etiquetasPorCard(List.of(card.getId())).getOrDefault(card.getId(), List.of()));
    }

    /**
     * Converte a lista inteira com sete consultas fixas, em vez de seis por card.
     *
     * <p>O kanban abre com dezenas de cards e cada um mostra o progresso do checklist e o nome do
     * analista. Resolver card a card seria N+1 — a mesma conta que já custou uma correção de
     * desempenho na triagem da praça de pagamento.</p>
     */
    private List<ProspeccaoResponse> toResponseEmLote(List<ProspeccaoEntity> cards) {
        if (cards.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = cards.stream().map(ProspeccaoEntity::getId).toList();
        Map<UUID, List<ProspeccaoDocumentoEntity>> documentosPorCard =
                documentoService.listarPorCards(ids);
        Map<UUID, java.math.BigDecimal> limites = decisaoService.limitesVigentes(ids);
        Map<UUID, Long> pendencias = pendenciaService.abertasPorCard(ids);
        var rodadas = comiteService.rodadaVigente(ids);
        var etiquetas = organizacaoService.etiquetasPorCard(ids);

        List<UUID> analistas = cards.stream()
                .map(ProspeccaoEntity::getAnalistaId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> nomes = analistas.isEmpty()
                ? Map.of()
                : userRepository.findAllById(analistas).stream()
                        .collect(Collectors.toMap(UserEntity::getId, UserEntity::getName));

        return cards.stream()
                .map(card -> mapper.toResponse(card,
                        card.getAnalistaId() == null ? null : nomes.get(card.getAnalistaId()),
                        documentosPorCard.getOrDefault(card.getId(), List.of()),
                        limites.get(card.getId()),
                        pendencias.getOrDefault(card.getId(), 0L),
                        rodadas.getOrDefault(card.getId(), List.of()),
                        etiquetas.getOrDefault(card.getId(), List.of())))
                .toList();
    }

    private List<ProspeccaoDocumentoResponse> documentos(UUID prospeccaoId) {
        Map<UUID, List<ProspeccaoArquivoEntity>> arquivos = arquivosPorDocumento(prospeccaoId);
        return documentoService.listar(prospeccaoId).stream()
                .map(documento -> mapper.toResponse(documento, arquivos))
                .toList();
    }

    private Map<UUID, List<ProspeccaoArquivoEntity>> arquivosPorDocumento(UUID prospeccaoId) {
        return arquivoService.listar(prospeccaoId).stream()
                .filter(arquivo -> arquivo.getDocumentoId() != null)
                .collect(Collectors.groupingBy(ProspeccaoArquivoEntity::getDocumentoId));
    }

    private String nomeDoUsuario(UUID usuarioId) {
        if (usuarioId == null) {
            return null;
        }
        return userRepository.findById(usuarioId).map(UserEntity::getName).orElse(null);
    }

    private UserEntity usuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            throw new com.portal.serasa.domain.exception.AcessoNegadoException("Não autenticado");
        }
        return userRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new com.portal.serasa.domain.exception.AcessoNegadoException("Usuário não encontrado"));
    }
}
