package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.exception.LimiteJuditException;
import com.portal.serasa.domain.model.PoloJudit;
import com.portal.serasa.domain.model.ServicoJudit;
import com.portal.serasa.domain.model.SituacaoConsultaJudit;
import com.portal.serasa.infrastructure.integration.judit.JuditClient;
import com.portal.serasa.infrastructure.integration.judit.JuditProperties;
import com.portal.serasa.infrastructure.persistence.entity.JuditConsultaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.JuditConsultaJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Processos judiciais pela Judit, sempre sob demanda: só chama quando um analista clica.
 *
 * <p>Funciona como o Serasa e o CNPJ Já: um botão na Visão 360º, sem ligar nada antes na empresa. Ligar
 * por empresa volta quando houver monitoramento. Duas travas antes de cada chamada cobrada:</p>
 * <ol>
 *   <li>o usuário tem a marca Judit (Settings, só admin marca);</li>
 *   <li>o gasto do mês mais o custo previsto não passa do limite do mês ({@link JuditFranquia}).</li>
 * </ol>
 *
 * <p>A consulta dos processos é o <b>On Demand</b> (Data Lake + tribunais ao vivo), só como ré por
 * padrão e como autora num pedido à parte. A <b>pré-consulta</b> é o resumo do Data Lake: opcional,
 * recomendada para empresa grande, dá o total como ré e com isso o custo estimado do On Demand. Ver
 * docs/judit/03-on-demand.md.</p>
 *
 * <p>A trava confere com o custo <i>estimado</i>: o real do On Demand só chega com a resposta. Ela
 * bloqueia as consultas seguintes, não a que estoura.</p>
 *
 * <p>Resultado não vence sozinho: depois de {@code judit.validade-dias} a tela marca como
 * desatualizado, mas só consulta de novo se alguém pedir.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JuditService {

    /** Chave da trava de transação que serializa as consultas cobradas. Qualquer número fixo serve. */
    static final long TRAVA_LIMITE = 7_310_489_201L;

    /** A Judit cobra o On Demand a cada bloco de 1.000 processos devolvidos. */
    static final int BLOCO = 1000;

    /** A partir daqui, dentro do bloco, o On Demand pode achar no tribunal o que vira o bloco seguinte. */
    static final int PERTO_DA_VIRADA = 900;

    private final JuditClient client;
    private final JuditProperties properties;
    private final JuditConsultaJpaRepository consultaRepository;
    private final JuditFranquia franquia;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventos;

    /** @param resumo a pré-consulta */
    public record Precos(BigDecimal resumo, BigDecimal onDemandPorMil, BigDecimal detalhe) {
    }

    /**
     * O que um On Demand deve custar.
     *
     * @param processos       quantos o resumo achou no polo; nulo sem pré-consulta, e aí vale um bloco
     * @param podeVirarBloco  perto da virada: o tribunal pode trazer o bastante para mais um bloco
     */
    public record Estimativa(Integer processos, int blocos, BigDecimal custo, boolean podeVirarBloco) {
    }

    /**
     * @param pontosDeAtencao o que olhar antes do On Demand (criminal, falência, execução fiscal...)
     * @param estimativaReu   custo estimado do On Demand como ré, por este resumo
     */
    public record ResumoConsultado(LocalDateTime consultadoEm, String consultadoPor, BigDecimal custo,
                                   boolean desatualizado, JuditLeitura.Resumo dados,
                                   List<JuditLeitura.PontoDeAtencao> pontosDeAtencao,
                                   Estimativa estimativaReu, Estimativa estimativaAutor) {
    }

    /**
     * @param onDemand      a lista veio do On Demand; falso é a lista antiga do Data Lake
     * @param somenteReu    só os processos em que a empresa é ré: Data Lake acima de 1.000, ou On Demand
     *                      sem o pedido como autora
     * @param totalNoResumo total que o resumo informou; a lista do Data Lake pode vir menor. Nulo no On Demand
     * @param detalhes      situação do último detalhe de cada processo detalhado, por número CNJ
     */
    public record ProcessosConsultados(LocalDateTime consultadoEm, String consultadoPor, BigDecimal custo,
                                       boolean desatualizado, boolean onDemand, boolean somenteReu,
                                       Integer totalNoResumo, JuditLeitura.Processos dados,
                                       Map<String, SituacaoConsultaJudit> detalhes) {
    }

    /**
     * O último On Demand de um polo. Enquanto um novo está pendente, a lista continua sendo a do último
     * concluído.
     *
     * @param comPreConsulta a estimativa saiu de uma pré-consulta; falso foi "consultar direto"
     * @param total          registros devolvidos (o que a Judit cobra); nulo enquanto pendente
     */
    public record OnDemandConsultado(PoloJudit polo, SituacaoConsultaJudit situacao, LocalDateTime pedidoEm,
                                     String pedidoPor, BigDecimal custoEstimado, BigDecimal custo,
                                     boolean comPreConsulta, LocalDateTime concluidoEm, String erro, Integer total,
                                     boolean desatualizado) {
    }

    /** O último detalhe pedido para um processo. {@code dados} nulo enquanto pendente ou se falhou. */
    public record DetalheConsultado(String cnj, SituacaoConsultaJudit situacao, LocalDateTime pedidoEm,
                                    String pedidoPor, BigDecimal custo, LocalDateTime concluidoEm, String erro,
                                    boolean desatualizado, JuditLeitura.Detalhe dados) {
    }

    public record Situacao(
            String cnpj,
            boolean podeUsar,
            boolean configurada,
            int validadeDias,
            JuditFranquia.Consumo consumo,
            Precos precos,
            ResumoConsultado resumo,
            ProcessosConsultados processos,
            OnDemandConsultado onDemandReu,
            OnDemandConsultado onDemandAutor) {
    }

    /** Um pedido assíncrono esperando a Judit. É o que o {@link JuditAcompanhador} acompanha. */
    public record Pendente(UUID consultaId, ServicoJudit servico, String requestId, UUID usuarioId, String documento,
                           String cnj, PoloJudit polo, String nomeEmpresa, LocalDateTime criadoEm,
                           LocalDateTime verificadoEm) {
    }

    /** "Verificar agora" da tela: pergunta à Judit uma vez, mesmo passado o tempo do acompanhamento. */
    public record VerificacaoPedida(Pendente pendente) {
    }

    /** @param processos processos distintos (instâncias juntas); {@code registros} é o que a Judit cobra */
    public record OnDemandConcluido(int processos, int registros, BigDecimal custo) {
    }

    @Transactional(readOnly = true)
    public Situacao situacao(String cnpj, UserEntity usuario) {
        String documento = cnpj(cnpj);
        LocalDateTime limiteValidade = LocalDateTime.now().minusDays(properties.validadeDias());

        ResumoConsultado resumo = consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.RESUMO)
                .map(c -> resumoDe(c, limiteValidade))
                .orElse(null);

        // Sem a lista: o card mostra só os totais, e a lista inteira pode passar de 1 MB.
        ProcessosConsultados processos = ultimaLista(documento, limiteValidade, false);

        return new Situacao(
                documento,
                usuario.isJudit(),
                properties.configurada(),
                properties.validadeDias(),
                franquia.consumo(),
                new Precos(properties.custoResumo(), properties.custoOnDemandPorMil(), properties.custoDetalhe()),
                resumo,
                processos,
                ultimoOnDemand(documento, PoloJudit.PASSIVO, limiteValidade),
                ultimoOnDemand(documento, PoloJudit.ATIVO, limiteValidade));
    }

    private ResumoConsultado resumoDe(JuditConsultaEntity c, LocalDateTime limiteValidade) {
        JuditLeitura.Resumo dados = JuditLeitura.resumo(c.getResposta());
        return new ResumoConsultado(c.getCriadoEm(), c.getUsuarioNome(), c.getCusto(),
                c.getCriadoEm().isBefore(limiteValidade), dados, JuditLeitura.pontosDeAtencao(dados),
                estimativa(dados.comoReu()), estimativa(dados.comoAutor()));
    }

    /** A lista unificada, inteira, para a página de processos. Não cobra. Nulo se nunca buscou. */
    @Transactional(readOnly = true)
    public ProcessosConsultados processos(String cnpj) {
        return ultimaLista(cnpj(cnpj), LocalDateTime.now().minusDays(properties.validadeDias()), true);
    }

    /**
     * Os últimos On Demand concluídos, ré e autora juntos. Sem nenhum, a última lista do Data Lake (fase
     * 1): a consulta saiu do portal, mas as já pagas continuam legíveis.
     */
    private ProcessosConsultados ultimaLista(String documento, LocalDateTime limiteValidade, boolean comLista) {
        List<JuditConsultaEntity> onDemand = concluidosOnDemand(documento);
        if (!onDemand.isEmpty()) {
            JsonNode junta = JuditLeitura.juntarListas(onDemand.stream().map(JuditConsultaEntity::getResposta).toList());
            JuditLeitura.Processos dados = JuditLeitura.processos(junta, documento);
            JuditConsultaEntity maisRecente = onDemand.stream()
                    .max(Comparator.comparing(JuditConsultaEntity::getCriadoEm)).orElseThrow();
            boolean algumVencido = onDemand.stream().anyMatch(c -> c.getCriadoEm().isBefore(limiteValidade));
            boolean somenteReu = onDemand.stream().noneMatch(c -> c.getPolo() == PoloJudit.ATIVO);
            return new ProcessosConsultados(maisRecente.getCriadoEm(), maisRecente.getUsuarioNome(),
                    onDemand.stream().map(JuditConsultaEntity::getCusto).reduce(BigDecimal.ZERO, BigDecimal::add),
                    algumVencido, true, somenteReu, null,
                    comLista ? dados : dados.semLista(),
                    comLista ? detalhesDa(documento) : Map.of());
        }
        return consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.PROCESSOS)
                .map(c -> {
                    JsonNode portal = c.getResposta().path("_portal");
                    JuditLeitura.Processos dados = JuditLeitura.processos(c.getResposta(), documento);
                    return new ProcessosConsultados(c.getCriadoEm(), c.getUsuarioNome(), c.getCusto(),
                            c.getCriadoEm().isBefore(limiteValidade), false,
                            portal.path("somente_reu").asBoolean(false),
                            portal.path("total_no_resumo").isInt() ? portal.path("total_no_resumo").asInt() : null,
                            comLista ? dados : dados.semLista(),
                            comLista ? detalhesDa(documento) : Map.of());
                })
                .orElse(null);
    }

    /** A lista crua que a tela mostra, para conferir se um processo é da empresa. Nula se nunca buscou. */
    private JsonNode listaCrua(String documento) {
        List<JuditConsultaEntity> onDemand = concluidosOnDemand(documento);
        if (!onDemand.isEmpty()) {
            return JuditLeitura.juntarListas(onDemand.stream().map(JuditConsultaEntity::getResposta).toList());
        }
        return consultaRepository.findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.PROCESSOS)
                .map(JuditConsultaEntity::getResposta)
                .orElse(null);
    }

    private List<JuditConsultaEntity> concluidosOnDemand(String documento) {
        List<JuditConsultaEntity> concluidos = new ArrayList<>(2);
        for (PoloJudit polo : PoloJudit.values()) {
            consultaRepository.findFirstByDocumentoAndServicoAndPoloAndSituacaoOrderByCriadoEmDesc(
                    documento, ServicoJudit.ON_DEMAND, polo, SituacaoConsultaJudit.CONCLUIDA).ifPresent(concluidos::add);
        }
        return concluidos;
    }

    private OnDemandConsultado ultimoOnDemand(String documento, PoloJudit polo, LocalDateTime limiteValidade) {
        return consultaRepository.findFirstByDocumentoAndServicoAndPoloOrderByCriadoEmDesc(documento, ServicoJudit.ON_DEMAND, polo)
                .map(c -> new OnDemandConsultado(polo, c.getSituacao(), c.getCriadoEm(), c.getUsuarioNome(),
                        c.getCustoEstimado(), c.getCusto(), c.getPreConsultaId() != null, c.getConcluidaEm(), c.getErro(),
                        c.getSituacao() == SituacaoConsultaJudit.CONCLUIDA ? c.getTotalProcessos() : null,
                        c.getSituacao() == SituacaoConsultaJudit.CONCLUIDA && c.getCriadoEm().isBefore(limiteValidade)))
                .orElse(null);
    }

    /** O último detalhe de cada processo; a consulta vem do mais recente para o mais antigo. */
    private Map<String, SituacaoConsultaJudit> detalhesDa(String documento) {
        Map<String, SituacaoConsultaJudit> detalhes = new LinkedHashMap<>();
        consultaRepository.detalhesDa(documento).forEach(d -> detalhes.putIfAbsent(d.getCnj(), d.getSituacao()));
        return detalhes;
    }

    // ── On Demand ────────────────────────────────────────────────────────────────────────────────

    /**
     * Pede à Judit os processos do polo, no Data Lake e nos tribunais. Cobra na hora (a Judit cobra o
     * pedido) com o custo estimado e volta PENDENTE; o {@link JuditAcompanhador} grava o custo real,
     * conclui e avisa. Pedir de novo o mesmo polo enquanto pendente não cobra outra vez.
     *
     * <p>A estimativa sai da pré-consulta dentro da validade, se houver; sem ela, um bloco — e a consulta
     * fica marcada como feita sem pré-consulta, para a auditoria do painel.</p>
     */
    @Transactional
    public Situacao consultarOnDemand(String cnpj, PoloJudit polo, String nomeEmpresa, UserEntity usuario) {
        String documento = cnpj(cnpj);
        if (polo == null) {
            throw new IllegalArgumentException("Informe o polo da consulta: PASSIVO (ré) ou ATIVO (autora).");
        }
        exigirMarca(usuario);
        Optional<JuditConsultaEntity> anterior = consultaRepository
                .findFirstByDocumentoAndServicoAndPoloOrderByCriadoEmDesc(documento, ServicoJudit.ON_DEMAND, polo);
        if (anterior.isPresent() && anterior.get().getSituacao() == SituacaoConsultaJudit.PENDENTE) {
            return situacao(documento, usuario);
        }

        JuditConsultaEntity preConsulta = consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.RESUMO)
                .filter(c -> !c.getCriadoEm().isBefore(LocalDateTime.now().minusDays(properties.validadeDias())))
                .orElse(null);
        Estimativa estimativa = estimativa(preConsulta == null ? null : processosNoPolo(preConsulta, polo));

        prepararConsulta(usuario, estimativa.custo());
        String requestId = client.pedirOnDemand(documento, polo);
        consultaRepository.save(JuditConsultaEntity.builder()
                .documento(documento)
                .cnpjEmpresa(documento)
                .servico(ServicoJudit.ON_DEMAND)
                .polo(polo)
                .situacao(SituacaoConsultaJudit.PENDENTE)
                .custo(estimativa.custo())
                .custoEstimado(estimativa.custo())
                .preConsultaId(preConsulta == null ? null : preConsulta.getId())
                .nomeEmpresa(nomeOuNulo(nomeEmpresa))
                .requestId(requestId)
                .usuarioId(usuario.getId())
                .usuarioNome(usuario.getName())
                .criadoEm(LocalDateTime.now())
                .build());
        log.info("Judit ON_DEMAND {} de {} pedido por {}: request {}, estimativa {}{}", polo, documento,
                usuario.getEmail(), requestId, estimativa.custo(), preConsulta == null ? " (sem pré-consulta)" : "");
        return situacao(documento, usuario);
    }

    /** Retoma o acompanhamento de um On Demand pendente. Não cobra: só pergunta de novo. */
    @Transactional(readOnly = true)
    public Situacao verificarOnDemand(String cnpj, PoloJudit polo, UserEntity usuario) {
        String documento = cnpj(cnpj);
        JuditConsultaEntity consulta = consultaRepository
                .findFirstByDocumentoAndServicoAndPoloOrderByCriadoEmDesc(documento, ServicoJudit.ON_DEMAND, polo)
                .orElseThrow(() -> new IllegalArgumentException("Esta empresa ainda não foi consultada neste polo."));
        if (consulta.getSituacao() == SituacaoConsultaJudit.PENDENTE) {
            eventos.publishEvent(new VerificacaoPedida(pendenteDe(consulta)));
        }
        return situacao(documento, usuario);
    }

    /**
     * Grava a lista no formato do Data Lake e troca o custo estimado pelo real: um bloco a cada 1.000
     * registros devolvidos, mínimo um. Nulo se outro acompanhamento já concluiu — quem recebe nulo não
     * notifica.
     */
    @Transactional
    public OnDemandConcluido concluirOnDemand(UUID consultaId, JsonNode respostas) {
        JuditConsultaEntity consulta = consultaRepository.travar(consultaId).orElse(null);
        if (consulta == null || consulta.getSituacao() != SituacaoConsultaJudit.PENDENTE) {
            return null;
        }
        ObjectNode lista = JuditLeitura.listaDoOnDemand(respostas);
        int registros = respostas.path("all_count").asInt(JuditLeitura.registros(lista));
        BigDecimal custo = custoPorBlocos(registros, properties.custoOnDemandPorMil());
        ObjectNode portal = lista.putObject("_portal");
        portal.put("polo", consulta.getPolo() == null ? null : consulta.getPolo().name());
        portal.put("all_count", registros);
        portal.put("request_id", consulta.getRequestId());

        consulta.setResposta(lista);
        consulta.setTotalProcessos(registros);
        consulta.setCusto(custo);
        consulta.setSituacao(SituacaoConsultaJudit.CONCLUIDA);
        consulta.setConcluidaEm(LocalDateTime.now());
        consultaRepository.save(consulta);
        if (consulta.getCustoEstimado() != null && custo.compareTo(consulta.getCustoEstimado()) != 0) {
            log.warn("Judit ON_DEMAND {} de {}: estimado {}, real {} ({} registros)", consulta.getPolo(),
                    consulta.getDocumento(), consulta.getCustoEstimado(), custo, registros);
        }
        int processos = JuditLeitura.processos(lista, consulta.getDocumento()).quantidade();
        return new OnDemandConcluido(processos, registros, custo);
    }

    private static int processosNoPolo(JuditConsultaEntity preConsulta, PoloJudit polo) {
        JuditLeitura.Resumo resumo = JuditLeitura.resumo(preConsulta.getResposta());
        return polo == PoloJudit.PASSIVO ? resumo.comoReu() : resumo.comoAutor();
    }

    /** Sem pré-consulta ({@code processos} nulo) vale um bloco. Zero também é um bloco: mínimo por pedido. */
    Estimativa estimativa(Integer processos) {
        BigDecimal porMil = properties.custoOnDemandPorMil();
        if (processos == null) {
            return new Estimativa(null, 1, porMil, false);
        }
        int blocos = blocos(processos);
        int noBloco = processos % BLOCO;
        boolean pertoDaVirada = processos > 0 && (noBloco == 0 || noBloco > PERTO_DA_VIRADA);
        return new Estimativa(processos, blocos, porMil.multiply(BigDecimal.valueOf(blocos)), pertoDaVirada);
    }

    // ── Detalhe de um processo ───────────────────────────────────────────────────────────────────

    /** O último detalhe do processo. Não cobra. Nulo se nunca foi detalhado. */
    @Transactional(readOnly = true)
    public DetalheConsultado detalhe(String cnpj, String cnj) {
        return consultaRepository
                .findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(cnpj(cnpj), numeroCnj(cnj), ServicoJudit.DETALHE)
                .map(this::detalheDe)
                .orElse(null);
    }

    /**
     * Pede à Judit o processo direto no tribunal, com resumo de IA. Cobra na hora (a Judit cobra o
     * pedido) e volta PENDENTE; o {@link JuditAcompanhador} conclui e notifica. Pedir de novo enquanto
     * o anterior está pendente não cobra outra vez.
     */
    @Transactional
    public DetalheConsultado detalhar(String cnpj, String cnj, String nomeEmpresa, UserEntity usuario) {
        String documento = cnpj(cnpj);
        String numero = numeroCnj(cnj);
        exigirMarca(usuario);
        JsonNode lista = listaCrua(documento);
        boolean naLista = lista != null && JuditLeitura.processos(lista, documento).lista().stream()
                .anyMatch(p -> numero.equals(p.cnj()));
        if (!naLista) {
            throw new IllegalArgumentException("Este processo não está na lista da empresa. Consulte os processos antes.");
        }
        var anterior = consultaRepository.findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(
                documento, numero, ServicoJudit.DETALHE);
        if (anterior.isPresent() && anterior.get().getSituacao() == SituacaoConsultaJudit.PENDENTE) {
            return detalheDe(anterior.get());
        }

        prepararConsulta(usuario, properties.custoDetalhe());
        String requestId = client.pedirDetalhe(numero);
        JuditConsultaEntity consulta = consultaRepository.save(JuditConsultaEntity.builder()
                .documento(documento)
                .cnpjEmpresa(documento)
                .cnj(numero)
                .servico(ServicoJudit.DETALHE)
                .situacao(SituacaoConsultaJudit.PENDENTE)
                .custo(properties.custoDetalhe())
                .nomeEmpresa(nomeOuNulo(nomeEmpresa))
                .requestId(requestId)
                .usuarioId(usuario.getId())
                .usuarioNome(usuario.getName())
                .criadoEm(LocalDateTime.now())
                .build());
        log.info("Judit DETALHE de {} ({}) pedido por {}: request {}, custo {}",
                numero, documento, usuario.getEmail(), requestId, properties.custoDetalhe());
        return detalheDe(consulta);
    }

    /**
     * Retoma o acompanhamento de um detalhe pendente — a Judit pode ter passado do tempo do
     * acompanhamento. Não cobra: só pergunta de novo.
     */
    @Transactional(readOnly = true)
    public DetalheConsultado verificar(String cnpj, String cnj) {
        String documento = cnpj(cnpj);
        JuditConsultaEntity consulta = consultaRepository
                .findFirstByDocumentoAndCnjAndServicoOrderByCriadoEmDesc(documento, numeroCnj(cnj), ServicoJudit.DETALHE)
                .orElseThrow(() -> new IllegalArgumentException("Este processo ainda não foi detalhado."));
        if (consulta.getSituacao() == SituacaoConsultaJudit.PENDENTE) {
            eventos.publishEvent(new VerificacaoPedida(pendenteDe(consulta)));
        }
        return detalheDe(consulta);
    }

    /** Grava a resposta. Falso se outro acompanhamento já concluiu — quem recebe falso não notifica. */
    @Transactional
    public boolean concluirDetalhe(UUID consultaId, JsonNode respostas) {
        JuditConsultaEntity consulta = consultaRepository.travar(consultaId).orElse(null);
        if (consulta == null || consulta.getSituacao() != SituacaoConsultaJudit.PENDENTE) {
            return false;
        }
        consulta.setResposta(respostas);
        consulta.setSituacao(SituacaoConsultaJudit.CONCLUIDA);
        consulta.setConcluidaEm(LocalDateTime.now());
        consultaRepository.save(consulta);
        return true;
    }

    private DetalheConsultado detalheDe(JuditConsultaEntity c) {
        boolean desatualizado = c.getSituacao() == SituacaoConsultaJudit.CONCLUIDA
                && c.getCriadoEm().isBefore(LocalDateTime.now().minusDays(properties.validadeDias()));
        return new DetalheConsultado(c.getCnj(), c.getSituacao(), c.getCriadoEm(), c.getUsuarioNome(), c.getCusto(),
                c.getConcluidaEm(), c.getErro(), desatualizado,
                c.getResposta() == null ? null : JuditLeitura.detalhe(c.getResposta()));
    }

    /** CNJ tem 20 dígitos. Guarda como a lista mostra (com pontos e traço), que é o que a tela manda. */
    private static String numeroCnj(String valor) {
        String numero = valor == null ? "" : valor.strip();
        if (numero.replaceAll("\\D", "").length() != 20) {
            throw new IllegalArgumentException("Número de processo inválido: o CNJ tem 20 dígitos.");
        }
        return numero;
    }

    // ── Acompanhamento dos pedidos assíncronos ───────────────────────────────────────────────────

    /**
     * Os pedidos ainda pendentes criados desde {@code desde}. O {@link JuditAcompanhador} decide, pela
     * idade e pela última pergunta, quais já é hora de perguntar.
     */
    @Transactional(readOnly = true)
    public List<Pendente> pendentesDesde(LocalDateTime desde) {
        return consultaRepository
                .findBySituacaoAndCriadoEmAfterOrderByCriadoEmAsc(SituacaoConsultaJudit.PENDENTE, desde)
                .stream()
                .map(JuditService::pendenteDe)
                .toList();
    }

    @Transactional
    public void marcarVerificado(UUID consultaId, LocalDateTime quando) {
        consultaRepository.marcarVerificado(consultaId, quando);
    }

    /** Marca como falha. O custo fica: a Judit cobra o pedido. Falso se já não estava pendente. */
    @Transactional
    public boolean falhar(UUID consultaId, String erro) {
        JuditConsultaEntity consulta = consultaRepository.travar(consultaId).orElse(null);
        if (consulta == null || consulta.getSituacao() != SituacaoConsultaJudit.PENDENTE) {
            return false;
        }
        consulta.setSituacao(SituacaoConsultaJudit.FALHOU);
        consulta.setErro(erro);
        consulta.setConcluidaEm(LocalDateTime.now());
        consultaRepository.save(consulta);
        return true;
    }

    private static Pendente pendenteDe(JuditConsultaEntity c) {
        return new Pendente(c.getId(), c.getServico(), c.getRequestId(), c.getUsuarioId(), c.getDocumento(), c.getCnj(),
                c.getPolo(), c.getNomeEmpresa(), c.getCriadoEm(), c.getVerificadoEm());
    }

    // ── Pré-consulta ─────────────────────────────────────────────────────────────────────────────

    /** Pré-consulta: o resumo processual do Data Lake (total e agregados). Síncrona, preço fixo por documento. */
    @Transactional
    public Situacao consultarResumo(String cnpj, String nomeEmpresa, UserEntity usuario) {
        String documento = cnpj(cnpj);
        prepararConsulta(usuario, properties.custoResumo());

        JsonNode resposta;
        try {
            resposta = client.resumo(documento);
        } catch (ExternalApiException e) {
            if (e.getUpstreamStatus() != 404) {
                throw e;
            }
            // Empresa sem nada na base da Judit. Não sabemos ainda se o 404 é cobrado — contamos como
            // se fosse, para o limite errar para o lado seguro. Pergunta registrada para a Judit.
            ObjectNode vazio = objectMapper.createObjectNode();
            vazio.put("lawsuits_count", 0);
            vazio.put("secrecy_lawsuits_count", 0);
            vazio.put("nao_encontrado", true);
            resposta = vazio;
        }

        int total = resposta.path("lawsuits_count").asInt(0);
        registrar(documento, ServicoJudit.RESUMO, properties.custoResumo(), total, resposta, nomeEmpresa, usuario);
        return situacao(documento, usuario);
    }

    /** Um bloco a cada 1.000 registros devolvidos; lista vazia também conta um, até a Judit dizer o contrário. */
    private static BigDecimal custoPorBlocos(int registros, BigDecimal porMil) {
        return porMil.multiply(BigDecimal.valueOf(blocos(registros)));
    }

    private static int blocos(int registros) {
        return Math.max(1, (registros + BLOCO - 1) / BLOCO);
    }

    // ── Travas ───────────────────────────────────────────────────────────────────────────────────

    /** Marca, chave configurada e limite do mês — nessa ordem. */
    private void prepararConsulta(UserEntity usuario, BigDecimal custoPrevisto) {
        exigirMarca(usuario);
        if (!properties.configurada()) {
            throw new ExternalApiException(JuditClient.PROVIDER, 503,
                    "A chave da Judit não está configurada no servidor (JUDIT_API_KEY). Avise o suporte.", null);
        }

        // A trava fica até o commit, que é depois de gravar a consulta: a próxima já soma esta.
        consultaRepository.travarLimite(TRAVA_LIMITE);
        BigDecimal gasto = franquia.gastoDoMes();
        BigDecimal limite = franquia.limiteDoMes();
        if (gasto.add(custoPrevisto).compareTo(limite) > 0) {
            throw new LimiteJuditException(String.format(
                    "Limite do mês da Judit atingido: %s gastos de %s. Esta consulta custaria %s. "
                            + "Peça ao admin para liberar um valor a mais no mês.",
                    reais(gasto), reais(limite), reais(custoPrevisto)));
        }
    }

    private void registrar(String documento, ServicoJudit servico, BigDecimal custo, int total,
                           JsonNode resposta, String nomeEmpresa, UserEntity usuario) {
        consultaRepository.save(JuditConsultaEntity.builder()
                .documento(documento)
                .cnpjEmpresa(documento)
                .servico(servico)
                .custo(custo)
                .totalProcessos(total)
                .requestId(resposta.path("request_id").isTextual() ? resposta.path("request_id").asText() : null)
                .resposta(resposta)
                .nomeEmpresa(nomeOuNulo(nomeEmpresa))
                .usuarioId(usuario.getId())
                .usuarioNome(usuario.getName())
                .criadoEm(LocalDateTime.now())
                .build());
        log.info("Judit {} de {} por {}: {} processo(s), custo {}", servico, documento, usuario.getEmail(), total, custo);
    }

    private static void exigirMarca(UserEntity usuario) {
        if (!usuario.isJudit()) {
            throw new AcessoNegadoException("Seu usuário não tem a marca Judit. Peça ao administrador em Configurações.");
        }
    }

    private static String cnpj(String valor) {
        String documento = valor == null ? "" : valor.replaceAll("\\D", "");
        if (documento.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve ter 14 dígitos.");
        }
        return documento;
    }

    private static String nomeOuNulo(String nome) {
        return nome == null || nome.isBlank() ? null : nome.strip();
    }

    private static String reais(BigDecimal valor) {
        // O formatador separa "R$" com espaço não separável; na mensagem da tela vale o espaço comum.
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor).replace(' ', ' ');
    }
}
