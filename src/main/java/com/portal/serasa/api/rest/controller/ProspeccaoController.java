package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.DocumentoTipoRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoCreateRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoDocumentoStatusRequest;
import com.portal.serasa.api.rest.dto.request.ProspeccaoEventoRequest;
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
 * Esteira de prospecção: da solicitação da análise até a documentação completa.
 *
 * <p>Substitui as abas VISÃO CEDENTE e DOC'S PENDENTES da planilha de controle. A esteira de
 * habilitação (dossiê, comitê, contrato, SEC, QITech) continua fora daqui — o card sai em
 * PRONTO_HABILITACAO.</p>
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
    private final ProspeccaoArquivoService arquivoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ProspeccaoEntradaAutomatica entradaAutomatica;
    private final ProspeccaoExportService exportService;
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
                .slaEmAtencao(abertos.stream().filter(ProspeccaoResponse::slaEmAtencao).count())
                .silencioProlongado(abertos.stream().filter(ProspeccaoResponse::silencioProlongado).count())
                .precisamAtencao(abertos.stream()
                        .filter(card -> card.slaEstourado() || card.silencioProlongado())
                        .count())
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
        return ResponseEntity.ok(ProspeccaoDetalheResponse.builder()
                .card(toResponse(card))
                .documentos(documentos(id))
                .timeline(prospeccaoService.timeline(id).stream().map(mapper::toResponse).toList())
                .destinosPossiveis(card.getEstagio().destinos().stream()
                        .sorted(Comparator.comparing(EstagioProspeccao::name))
                        .toList())
                .build());
    }

    // --------------------------------------------------------------- escrita

    @PostMapping
    public ResponseEntity<ProspeccaoResponse> criar(@Valid @RequestBody ProspeccaoCreateRequest request) {
        UserEntity autor = usuarioAutenticado();
        autorizacao.exigirCriador(autor);

        // Comercial que abre sem informar o responsável fica como responsável — evita card órfão.
        UUID comercialId = request.comercialId() != null ? request.comercialId() : autor.getId();
        String comercialNome = request.comercialNome() != null && !request.comercialNome().isBlank()
                ? request.comercialNome()
                : nomeDoUsuario(comercialId);

        ProspeccaoEntity card = prospeccaoService.criar(
                request.cnpj(), request.razaoSocial(), comercialId, comercialNome, autor);
        return ResponseEntity.status(201).body(toResponse(card));
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
        tipo.setSomenteUf(request.somenteUf());
        tipo.setAtivo(request.ativo());
        tipo.setOrdem(request.ordem());
        tipo.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(mapper.toResponse(documentoTipoRepository.save(tipo)));
    }

    // -------------------------------------------------------------- internals

    private ProspeccaoResponse toResponse(ProspeccaoEntity card) {
        return mapper.toResponse(card, nomeDoUsuario(card.getAnalistaId()),
                documentoService.listar(card.getId()));
    }

    /**
     * Converte a lista inteira com três consultas fixas, em vez de duas por card.
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
                        documentosPorCard.getOrDefault(card.getId(), List.of())))
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
