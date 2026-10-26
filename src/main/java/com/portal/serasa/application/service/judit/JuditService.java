package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.exception.LimiteJuditException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Processos judiciais pela Judit, sempre sob demanda: só chama quando um analista clica.
 *
 * <p>Funciona como o Serasa e o CNPJ Já: um botão na Visão 360º, sem ligar nada antes na empresa. Ligar
 * por empresa volta quando houver monitoramento. Duas travas antes de cada chamada cobrada:</p>
 * <ol>
 *   <li>o usuário tem a marca Judit (Settings, só admin marca);</li>
 *   <li>o gasto do mês mais o custo da chamada não passa do limite do mês ({@link JuditFranquia}).</li>
 * </ol>
 *
 * <p>As duas consultas por documento são da base histórica da Judit (Data Lake), não dos tribunais em
 * tempo real: o resumo e, depois dele, a lista. O On Demand foi testado e ficou de fora (decisão de
 * 2026-10-26, docs/judit/03-on-demand.md).</p>
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

    /** Acima disso a rota corta em 1.000 sem avisar; filtramos réu, que é o que pesa no crédito. */
    static final int CORTE_DA_ROTA = 1000;

    private final JuditClient client;
    private final JuditProperties properties;
    private final JuditConsultaJpaRepository consultaRepository;
    private final JuditFranquia franquia;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventos;

    public record Precos(BigDecimal resumo, BigDecimal processosPorMil, BigDecimal detalhe) {
    }

    /** @param pontosDeAtencao o que olhar no resumo: criminal, falência, execução fiscal... */
    public record ResumoConsultado(LocalDateTime consultadoEm, String consultadoPor, BigDecimal custo,
                                   boolean desatualizado, JuditLeitura.Resumo dados,
                                   List<JuditLeitura.PontoDeAtencao> pontosDeAtencao) {
    }

    /**
     * @param somenteReu    a consulta pediu só os processos em que a empresa é ré (empresa acima de 1.000)
     * @param totalNoResumo total que o resumo informou; a lista pode vir menor
     * @param detalhes      situação do último detalhe de cada processo detalhado, por número CNJ
     */
    public record ProcessosConsultados(LocalDateTime consultadoEm, String consultadoPor, BigDecimal custo,
                                       boolean desatualizado, boolean somenteReu, Integer totalNoResumo,
                                       JuditLeitura.Processos dados, Map<String, SituacaoConsultaJudit> detalhes) {
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
            ProcessosConsultados processos) {
    }

    /** Um detalhe esperando a Judit. É o que o {@link JuditAcompanhador} acompanha. */
    public record Pendente(UUID consultaId, String requestId, UUID usuarioId, String documento, String cnj,
                           String nomeEmpresa, LocalDateTime criadoEm, LocalDateTime verificadoEm) {
    }

    /** "Verificar agora" da tela: pergunta à Judit uma vez, mesmo passado o tempo do acompanhamento. */
    public record VerificacaoPedida(Pendente pendente) {
    }

    @Transactional(readOnly = true)
    public Situacao situacao(String cnpj, UserEntity usuario) {
        String documento = cnpj(cnpj);
        LocalDateTime limiteValidade = LocalDateTime.now().minusDays(properties.validadeDias());

        ResumoConsultado resumo = consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.RESUMO)
                .map(c -> {
                    JuditLeitura.Resumo dados = JuditLeitura.resumo(c.getResposta());
                    return new ResumoConsultado(c.getCriadoEm(), c.getUsuarioNome(), c.getCusto(),
                            c.getCriadoEm().isBefore(limiteValidade), dados, JuditLeitura.pontosDeAtencao(dados));
                })
                .orElse(null);

        // Sem a lista: o card mostra só os totais, e a lista inteira pode passar de 1 MB.
        ProcessosConsultados processos = ultimaLista(documento, limiteValidade, false);

        return new Situacao(
                documento,
                usuario.isJudit(),
                properties.configurada(),
                properties.validadeDias(),
                franquia.consumo(),
                new Precos(properties.custoResumo(), properties.custoProcessosPorMil(), properties.custoDetalhe()),
                resumo,
                processos);
    }

    /** A última lista consultada, inteira, para a página de processos. Não cobra. Nulo se nunca buscou. */
    @Transactional(readOnly = true)
    public ProcessosConsultados processos(String cnpj) {
        return ultimaLista(cnpj(cnpj), LocalDateTime.now().minusDays(properties.validadeDias()), true);
    }

    private ProcessosConsultados ultimaLista(String documento, LocalDateTime limiteValidade, boolean comLista) {
        return consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.PROCESSOS)
                .map(c -> {
                    JsonNode portal = c.getResposta().path("_portal");
                    JuditLeitura.Processos dados = JuditLeitura.processos(c.getResposta(), documento);
                    return new ProcessosConsultados(c.getCriadoEm(), c.getUsuarioNome(), c.getCusto(),
                            c.getCriadoEm().isBefore(limiteValidade),
                            portal.path("somente_reu").asBoolean(false),
                            portal.path("total_no_resumo").isInt() ? portal.path("total_no_resumo").asInt() : null,
                            comLista ? dados : dados.semLista(),
                            comLista ? detalhesDa(documento) : Map.of());
                })
                .orElse(null);
    }

    /** O último detalhe de cada processo; a consulta vem do mais recente para o mais antigo. */
    private Map<String, SituacaoConsultaJudit> detalhesDa(String documento) {
        Map<String, SituacaoConsultaJudit> detalhes = new LinkedHashMap<>();
        consultaRepository.detalhesDa(documento).forEach(d -> detalhes.putIfAbsent(d.getCnj(), d.getSituacao()));
        return detalhes;
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
     * pedido) e volta PENDENTE; o {@link JuditAcompanhador} acha o pedido no banco, conclui e notifica.
     * Pedir de novo enquanto o anterior está pendente não cobra outra vez.
     */
    @Transactional
    public DetalheConsultado detalhar(String cnpj, String cnj, String nomeEmpresa, UserEntity usuario) {
        String documento = cnpj(cnpj);
        String numero = numeroCnj(cnj);
        exigirMarca(usuario);
        boolean naLista = consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.PROCESSOS)
                .map(c -> JuditLeitura.processos(c.getResposta(), documento).lista().stream()
                        .anyMatch(p -> numero.equals(p.cnj())))
                .orElse(false);
        if (!naLista) {
            throw new IllegalArgumentException("Este processo não está na lista da empresa. Busque a lista antes.");
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

    /** Marca como falha. O custo fica: a Judit cobra o pedido. Falso se já não estava pendente. */
    @Transactional
    public boolean falharDetalhe(UUID consultaId, String erro) {
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

    /**
     * Os detalhes ainda pendentes criados desde {@code desde}. O {@link JuditAcompanhador} decide, pela
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

    private static Pendente pendenteDe(JuditConsultaEntity c) {
        return new Pendente(c.getId(), c.getRequestId(), c.getUsuarioId(), c.getDocumento(), c.getCnj(),
                c.getNomeEmpresa(), c.getCriadoEm(), c.getVerificadoEm());
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

    // ── Consultas por documento (Data Lake) ──────────────────────────────────────────────────────

    /** Resumo processual (total e agregados). Preço fixo por documento. */
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

    /**
     * A lista de processos. Exige o resumo antes: é ele que diz se há o que listar e se a empresa
     * passa do corte de 1.000 da rota.
     */
    @Transactional
    public Situacao consultarProcessos(String cnpj, String nomeEmpresa, UserEntity usuario) {
        String documento = cnpj(cnpj);
        JuditConsultaEntity resumo = consultaRepository
                .findFirstByDocumentoAndServicoOrderByCriadoEmDesc(documento, ServicoJudit.RESUMO)
                .orElseThrow(() -> new IllegalArgumentException("Consulte o resumo processual antes da lista."));
        int totalNoResumo = resumo.getResposta().path("lawsuits_count").asInt(0);
        if (totalNoResumo == 0) {
            throw new IllegalArgumentException("O resumo não encontrou processos: não há lista para buscar.");
        }

        // A rota devolve no máximo 1.000 registros, então a chamada custa um bloco.
        prepararConsulta(usuario, properties.custoProcessosPorMil());

        boolean somenteReu = totalNoResumo > CORTE_DA_ROTA;
        JsonNode resposta = client.processos(documento, somenteReu ? "Passive" : null);

        int registros = JuditLeitura.registros(resposta);
        BigDecimal custo = custoProcessos(registros);
        if (resposta instanceof ObjectNode objeto) {
            ObjectNode portal = objeto.putObject("_portal");
            portal.put("somente_reu", somenteReu);
            portal.put("total_no_resumo", totalNoResumo);
        }
        String nome = nomeEmpresa == null || nomeEmpresa.isBlank() ? resumo.getNomeEmpresa() : nomeEmpresa;
        registrar(documento, ServicoJudit.PROCESSOS, custo, registros, resposta, nome, usuario);
        return situacao(documento, usuario);
    }

    /** Um bloco a cada 1.000 registros devolvidos; lista vazia também conta um, até a Judit dizer o contrário. */
    BigDecimal custoProcessos(int registros) {
        int blocos = Math.max(1, (registros + CORTE_DA_ROTA - 1) / CORTE_DA_ROTA);
        return properties.custoProcessosPorMil().multiply(BigDecimal.valueOf(blocos));
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
