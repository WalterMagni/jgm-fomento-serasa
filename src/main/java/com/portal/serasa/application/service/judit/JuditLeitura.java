package com.portal.serasa.application.service.judit;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Transforma a resposta crua da Judit no que a tela mostra. Sem estado e sem banco: recebe o JSON
 * guardado em {@code judit_consulta.resposta}, então dá para mudar a leitura sem pagar consulta nova.
 *
 * <p>Tratamentos do docs/judit/01-mapa-de-campos.md, tirados de payload real:</p>
 * <ul>
 *   <li>{@code "NÃO INFORMADO"} é texto, não nulo — vira nulo aqui;</li>
 *   <li>o mesmo processo vem uma vez por instância — junta pelo número CNJ;</li>
 *   <li>{@code amount: 0} é valor ausente, não causa de valor zero;</li>
 *   <li>o polo sai do documento da parte ({@code main_document}), nunca do nome: o tribunal grava a
 *       razão social da época, e às vezes o nome do sócio no CNPJ da empresa. CNPJ casa pela raiz.</li>
 * </ul>
 */
public final class JuditLeitura {

    private static final String NAO_INFORMADO = "NÃO INFORMADO";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /**
     * Assuntos de quem diz que o título protestado ou negativado não era devido. Vários deles com a
     * empresa como ré é o desenho de duplicata sem lastro — o achado da primeira consulta de teste.
     */
    private static final Pattern ASSUNTO_TITULO_CONTESTADO = Pattern.compile(
            "PROTESTO|SUSTA[ÇC][ÃA]O|INCLUS[ÃA]O INDEVIDA|CADASTRO DE INADIMPLENTES|DUPLICATA|INEXIST[ÊE]NCIA DE D[ÉE]BITO",
            Pattern.CASE_INSENSITIVE);

    /**
     * Quem a Judit lista como parte sem ser parte: advogado (vem com documento OAB), sociedade de
     * advogados, procurador, perito, testemunha. O tipo é texto livre do cartório — "ADVOGADO
     * (EXECTDA)", "ADVOGADA (APELANTE)", "SOC. ADVOGADOS" — por isso a busca por trecho.
     */
    private static final Pattern NAO_E_PARTE = Pattern.compile(
            "ADVOGAD|SOC\\. ADVOGADOS|PROCURADOR|PERITO|TESTEMUNHA|JUIZ", Pattern.CASE_INSENSITIVE);

    public static final String REU = "REU";
    public static final String AUTOR = "AUTOR";
    public static final String TERCEIRO = "TERCEIRO";
    public static final String DESCONHECIDO = "DESCONHECIDO";

    private JuditLeitura() {
    }

    /** Valor de um agregado e quantas vezes aparece. {@code valor} nulo = "não informado" pela Judit. */
    public record Contagem(String valor, int quantidade) {
    }

    public record Resumo(
            int total,
            int emSegredo,
            int comoReu,
            int comoAutor,
            boolean naoEncontrado,
            List<Contagem> tribunais,
            List<Contagem> areas,
            List<Contagem> classes,
            List<Contagem> assuntos,
            List<Contagem> fases,
            List<Contagem> estados,
            /** Estadual, Federal, do Trabalho... */
            List<Contagem> justicas) {
    }

    /**
     * Algo no resumo que merece olhar antes do On Demand. Os agregados não se cruzam: "3 na área penal"
     * pode ser a empresa como vítima. {@code quantidade} é o maior número entre as dimensões que
     * acusaram (área, classe, assunto, justiça), para não somar o mesmo processo duas vezes.
     */
    public record PontoDeAtencao(String tipo, String titulo, int quantidade) {
    }

    public record Andamento(LocalDate data, String texto) {
    }

    public record Processo(
            String cnj,
            List<Integer> instancias,
            String autuacao,
            String polo,
            List<String> contrapartes,
            String tribunal,
            /** Vara, câmara, gabinete — o que o tribunal informou do órgão julgador. */
            List<String> orgaos,
            String uf,
            String cidade,
            String area,
            String classe,
            List<String> assuntos,
            BigDecimal valor,
            LocalDate distribuidoEm,
            Andamento ultimoAndamento,
            boolean emSegredo,
            LocalDate fonteAtualizadaEm,
            /** ESTADUAL, TRABALHO, FEDERAL, ELEITORAL, MILITAR, SUPERIOR ou OUTRA — pelo número CNJ. */
            String justica,
            /**
             * EM_TRAMITACAO, SUSPENSO ou ARQUIVADO, deduzida do último andamento: a base histórica da
             * Judit não traz a situação do processo. Nula sem andamento.
             */
            String situacao,
            /** Banco, financeira, FIDC ou securitizadora do outro lado (a etiqueta {@code banking} da Judit ou o nome). */
            boolean segmentoFinanceiro) {
    }

    public record Processos(
            int registros,
            int quantidade,
            int comoReu,
            int comoAutor,
            BigDecimal valorComoReu,
            int tituloContestadoComoReu,
            List<Contagem> assuntosComoReu,
            List<Processo> lista) {

        /** Os totais sem a lista: o card da Visão 360º não precisa dos processos, só a página deles. */
        public Processos semLista() {
            return new Processos(registros, quantidade, comoReu, comoAutor, valorComoReu,
                    tituloContestadoComoReu, assuntosComoReu, List.of());
        }
    }

    // ── Resumo (/lawsuits/synthetic) ─────────────────────────────────────────────────────────────

    public static Resumo resumo(JsonNode raiz) {
        List<Contagem> polos = contagens(raiz.path("sides"));
        return new Resumo(
                raiz.path("lawsuits_count").asInt(0),
                raiz.path("secrecy_lawsuits_count").asInt(0),
                quantidadeDe(polos, "PASSIVE"),
                quantidadeDe(polos, "ACTIVE"),
                raiz.path("nao_encontrado").asBoolean(false),
                contagens(raiz.path("tribunals")),
                contagens(raiz.path("areas")),
                contagens(raiz.path("classifications")),
                contagens(raiz.path("subjects")),
                contagens(raiz.path("phases")),
                contagens(raiz.path("states")),
                contagens(raiz.path("justices")));
    }

    // ── Pontos de atenção (da pré-consulta) ──────────────────────────────────────────────────────

    /** "Cláusula penal" é assunto cível (multa de contrato), não crime. "Discriminação" não é crime aqui. */
    private static final Pattern CRIMINAL = Pattern.compile(
            "(?<!CLAUSULA )\\bPENA(L|IS)?\\b|\\bCRIM|INQUERITO POLICIAL|TERMO CIRCUNSTANCIADO|CONTRAVENC");
    private static final Pattern EXECUCAO_FISCAL = Pattern.compile("EXECUCAO FISCAL|DIVIDA ATIVA");
    private static final Pattern RECUPERACAO_FALENCIA = Pattern.compile(
            "RECUPERACAO (JUDICIAL|EXTRAJUDICIAL)|FALENCIA|CONCORDATA|INSOLVENCIA");
    /** Só a justiça e a área exata: "DIREITO PROCESSUAL CIVIL E DO TRABALHO" é a árvore do processo civil. */
    private static final Pattern TRABALHISTA = Pattern.compile("^(JUSTICA DO TRABALHO|DIREITO DO TRABALHO)$");

    /** Na ordem em que a tela mostra: do mais grave ao mais comum. Só os que acusaram. */
    public static List<PontoDeAtencao> pontosDeAtencao(Resumo resumo) {
        List<PontoDeAtencao> pontos = new ArrayList<>();
        adicionar(pontos, "CRIMINAL", "Processo criminal",
                maior(CRIMINAL, resumo.areas(), resumo.classes(), resumo.assuntos()));
        adicionar(pontos, "RECUPERACAO_FALENCIA", "Recuperação judicial ou falência",
                maior(RECUPERACAO_FALENCIA, resumo.classes(), resumo.assuntos()));
        adicionar(pontos, "EXECUCAO_FISCAL", "Execução fiscal",
                maior(EXECUCAO_FISCAL, resumo.classes(), resumo.assuntos()));
        adicionar(pontos, "TITULO_CONTESTADO", "Protesto, negativação ou duplicata contestada",
                Math.min(resumo.total(), soma(ASSUNTO_TITULO_CONTESTADO, resumo.assuntos())));
        adicionar(pontos, "TRABALHISTA", "Justiça do Trabalho",
                maior(TRABALHISTA, resumo.justicas(), resumo.areas()));
        return pontos;
    }

    private static void adicionar(List<PontoDeAtencao> pontos, String tipo, String titulo, int quantidade) {
        if (quantidade > 0) {
            pontos.add(new PontoDeAtencao(tipo, titulo, quantidade));
        }
    }

    @SafeVarargs
    private static int maior(Pattern padrao, List<Contagem>... dimensoes) {
        int maior = 0;
        for (List<Contagem> dimensao : dimensoes) {
            maior = Math.max(maior, soma(padrao, dimensao));
        }
        return maior;
    }

    /** Dentro de uma dimensão os valores não se repetem num processo: dá para somar. */
    private static int soma(Pattern padrao, List<Contagem> dimensao) {
        return dimensao.stream()
                .filter(c -> c.valor() != null && padrao.matcher(semAcento(c.valor())).find())
                .mapToInt(Contagem::quantidade)
                .sum();
    }

    private static String semAcento(String valor) {
        return Normalizer.normalize(valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).strip();
    }

    /** Junta as grafias que viram "não informado" e ordena da maior contagem para a menor. */
    private static List<Contagem> contagens(JsonNode lista) {
        Map<String, Integer> somas = new LinkedHashMap<>();
        for (JsonNode item : lista) {
            String valor = texto(item.path("value"));
            somas.merge(valor == null ? "" : valor, item.path("count").asInt(0), Integer::sum);
        }
        return somas.entrySet().stream()
                .map(e -> new Contagem(e.getKey().isEmpty() ? null : e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(Contagem::quantidade).reversed())
                .toList();
    }

    private static int quantidadeDe(List<Contagem> contagens, String valor) {
        return contagens.stream()
                .filter(c -> valor.equalsIgnoreCase(c.valor()))
                .mapToInt(Contagem::quantidade)
                .sum();
    }

    // ── Processos (/lawsuits) ────────────────────────────────────────────────────────────────────

    /** Quantos registros vieram, antes de juntar as instâncias. É o que a Judit cobra. */
    public static int registros(JsonNode raiz) {
        return listaCrua(raiz).size();
    }

    public static Processos processos(JsonNode raiz, String documento) {
        List<JsonNode> registros = listaCrua(raiz);

        Map<String, List<JsonNode>> porCnj = new LinkedHashMap<>();
        for (JsonNode registro : registros) {
            String cnj = texto(registro.path("code"));
            porCnj.computeIfAbsent(cnj == null ? "sem-numero-" + porCnj.size() : cnj, k -> new ArrayList<>()).add(registro);
        }

        List<Processo> lista = porCnj.entrySet().stream()
                .map(e -> processo(e.getKey(), e.getValue(), documento))
                .sorted(Comparator.comparing(Processo::distribuidoEm, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<Processo> comoReu = lista.stream().filter(p -> REU.equals(p.polo())).toList();
        BigDecimal valorComoReu = comoReu.stream()
                .map(Processo::valor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int tituloContestado = (int) comoReu.stream()
                .filter(p -> p.assuntos().stream().anyMatch(a -> ASSUNTO_TITULO_CONTESTADO.matcher(a).find()))
                .count();

        Map<String, Integer> assuntosReu = new LinkedHashMap<>();
        comoReu.forEach(p -> p.assuntos().forEach(a -> assuntosReu.merge(a, 1, Integer::sum)));
        List<Contagem> topAssuntosReu = assuntosReu.entrySet().stream()
                .map(e -> new Contagem(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(Contagem::quantidade).reversed())
                .limit(5)
                .toList();

        return new Processos(
                registros.size(),
                lista.size(),
                comoReu.size(),
                (int) lista.stream().filter(p -> AUTOR.equals(p.polo())).count(),
                valorComoReu,
                tituloContestado,
                topAssuntosReu,
                lista);
    }

    /** A doc diz {@code response_data}; a rota síncrona devolveu {@code lawsuits}. Aceita os dois. */
    private static List<JsonNode> listaCrua(JsonNode raiz) {
        JsonNode lista = raiz.has("lawsuits") ? raiz.path("lawsuits") : raiz.path("response_data");
        List<JsonNode> registros = new ArrayList<>();
        lista.forEach(registros::add);
        return registros;
    }

    /** Um processo a partir das suas linhas (uma por instância). A capa vem da instância mais baixa. */
    private static Processo processo(String cnj, List<JsonNode> linhas, String documento) {
        List<JsonNode> ordenadas = linhas.stream()
                .sorted(Comparator.comparingInt(l -> l.path("instance").asInt(Integer.MAX_VALUE)))
                .toList();
        JsonNode capa = ordenadas.get(0);

        Set<Integer> instancias = new TreeSet<>();
        Set<String> assuntos = new LinkedHashSet<>();
        Set<String> orgaos = new LinkedHashSet<>();
        Set<String> ladosDoDocumento = new LinkedHashSet<>();
        Map<String, List<String>> nomesPorLado = new LinkedHashMap<>();
        LocalDate distribuidoEm = null;
        LocalDate fonteAtualizadaEm = null;
        Andamento ultimo = null;
        boolean emSegredo = false;
        boolean bancario = false;

        for (JsonNode linha : ordenadas) {
            if (linha.has("instance")) {
                instancias.add(linha.path("instance").asInt());
            }
            bancario |= linha.path("tags").path("banking").asBoolean(false);
            linha.path("subjects").forEach(s -> {
                String nome = texto(s.path("name"));
                if (nome != null) {
                    assuntos.add(nome);
                }
            });
            String orgao = texto(linha.path("county"));
            if (orgao != null) {
                orgaos.add(orgao);
            }
            linha.path("courts").forEach(c -> {
                String nome = texto(c.path("name"));
                if (nome != null) {
                    orgaos.add(nome);
                }
            });
            for (JsonNode parte : linha.path("parties")) {
                String lado = texto(parte.path("side"));
                if (mesmaPessoa(documento, digitos(texto(parte.path("main_document"))))) {
                    if (lado != null) {
                        ladosDoDocumento.add(lado);
                    }
                } else if (!auxiliar(parte)) {
                    String nome = texto(parte.path("name"));
                    if (nome != null && lado != null) {
                        List<String> nomes = nomesPorLado.computeIfAbsent(lado, k -> new ArrayList<>());
                        if (!nomes.contains(nome)) {
                            nomes.add(nome);
                        }
                    }
                }
            }
            distribuidoEm = menor(distribuidoEm, data(linha.path("distribution_date")));
            fonteAtualizadaEm = maior(fonteAtualizadaEm, data(linha.path("crawler").path("updated_at")));
            emSegredo |= linha.path("secrecy_level").asInt(0) > 0;

            JsonNode passo = linha.path("last_step");
            LocalDate dataPasso = data(passo.path("step_date"));
            if (dataPasso != null && (ultimo == null || ultimo.data() == null || dataPasso.isAfter(ultimo.data()))) {
                ultimo = new Andamento(dataPasso, texto(passo.path("content")));
            }
        }

        String polo = polo(ladosDoDocumento);
        String ladoOposto = switch (polo) {
            case REU -> "Active";
            case AUTOR -> "Passive";
            default -> null;
        };
        List<String> contrapartes = ladoOposto == null
                ? List.of()
                : List.copyOf(nomesPorLado.getOrDefault(ladoOposto, List.of()));

        JsonNode classes = capa.path("classifications");
        BigDecimal valor = capa.path("amount").isNumber() && capa.path("amount").decimalValue().signum() > 0
                ? capa.path("amount").decimalValue()
                : null;
        String numero = cnj.startsWith("sem-numero-") ? null : cnj;
        boolean financeiro = bancario || contrapartes.stream().anyMatch(n -> FINANCEIRO.matcher(semAcento(n)).find());

        return new Processo(
                numero,
                List.copyOf(instancias),
                texto(capa.path("name")),
                polo,
                contrapartes,
                texto(capa.path("tribunal_acronym")),
                List.copyOf(orgaos),
                texto(capa.path("state")),
                texto(capa.path("city")),
                texto(capa.path("area")),
                classes.isArray() && !classes.isEmpty() ? texto(classes.get(0).path("name")) : null,
                List.copyOf(assuntos),
                valor,
                distribuidoEm,
                ultimo,
                emSegredo,
                fonteAtualizadaEm,
                justica(numero, texto(capa.path("justice_description"))),
                situacao(texto(capa.path("situation")), ultimo == null ? null : ultimo.texto()),
                financeiro);
    }

    // ── Justiça, situação e segmento financeiro ──────────────────────────────────────────────────

    /** NNNNNNN-DD.AAAA.J.TR.OOOO — o J é o segmento da Justiça (Resolução CNJ 65/2008). */
    private static final Pattern NUMERO_CNJ = Pattern.compile("^\\d{7}-\\d{2}\\.\\d{4}\\.(\\d)\\.\\d{2}\\.\\d{4}$");

    /** Pelo número CNJ; sem número no padrão, pela descrição da Judit ("JUSTIÇA ESTADUAL"). */
    static String justica(String cnj, String descricao) {
        Matcher m = cnj == null ? null : NUMERO_CNJ.matcher(cnj);
        if (m != null && m.matches()) {
            return switch (m.group(1)) {
                case "8" -> "ESTADUAL";
                case "5" -> "TRABALHO";
                case "4" -> "FEDERAL";
                case "6" -> "ELEITORAL";
                case "7", "9" -> "MILITAR";
                default -> "SUPERIOR";
            };
        }
        String d = descricao == null ? "" : semAcento(descricao);
        if (d.contains("ESTADUAL")) return "ESTADUAL";
        if (d.contains("TRABALHO")) return "TRABALHO";
        if (d.contains("FEDERAL")) return "FEDERAL";
        if (d.contains("ELEITORAL")) return "ELEITORAL";
        if (d.contains("MILITAR")) return "MILITAR";
        return "OUTRA";
    }

    /** Suspensão e arquivamento provisório: a execução parou (costuma ser falta de bens), mas não acabou. */
    private static final Pattern SUSPENSO = Pattern.compile(
            "ARQUIV\\w* PROVISORI|PROVISORIAMENTE ARQUIV|SUSPENS|SOBRESTA");
    private static final Pattern REATIVADO = Pattern.compile("DESARQUIV|REATIVA");
    private static final Pattern ARQUIVADO = Pattern.compile(
            "ARQUIV|BAIXA DEFINITIVA|BAIXADO DEFINITIVAMENTE|EXTINT|TRANSIT\\w* EM JULGADO|PROCESSO FINDO");

    /**
     * A situação provável do processo. A base histórica da Judit traz "NÃO INFORMADO" na situação, então
     * vale o texto do último andamento: arquivado, baixado, extinto ou transitado em julgado encerra;
     * arquivamento provisório, suspensão ou sobrestamento suspende; o resto está em tramitação. É
     * dedução — a tela avisa.
     */
    static String situacao(String situacaoDaJudit, String ultimoAndamento) {
        for (String texto : new String[] {situacaoDaJudit, ultimoAndamento}) {
            if (texto == null) {
                continue;
            }
            String t = semAcento(texto);
            if (SUSPENSO.matcher(t).find()) return "SUSPENSO";
            if (REATIVADO.matcher(t).find()) return "EM_TRAMITACAO";
            if (ARQUIVADO.matcher(t).find()) return "ARQUIVADO";
        }
        return ultimoAndamento == null ? null : "EM_TRAMITACAO";
    }

    /** Quem do outro lado é do mercado financeiro. "Crédito" sozinho pega cobradora; vale como sinal. */
    private static final Pattern FINANCEIRO = Pattern.compile(
            "\\bBANCO\\b|\\bBANK\\b|FINANCEIRA|\\bFIDC\\b|FUNDO DE INVESTIMENTO|SECURITIZ|COOPERATIVA DE CREDITO|"
                    + "\\bCREDITO\\b|FOMENTO|FACTORING|CAIXA ECONOMICA|LEASING|ARRENDAMENTO MERCANTIL|\\bS\\.?A\\.? CFI\\b");

    private static boolean auxiliar(JsonNode parte) {
        String tipo = texto(parte.path("person_type"));
        if (tipo != null && NAO_E_PARTE.matcher(tipo).find()) {
            return true;
        }
        for (JsonNode documento : parte.path("documents")) {
            if ("oab".equalsIgnoreCase(documento.path("document_type").asText())) {
                return true;
            }
        }
        return false;
    }

    /** Réu ganha de autor: num processo em que a empresa aparece dos dois lados, o risco é ser ré. */
    private static String polo(Set<String> lados) {
        if (lados.stream().anyMatch("Passive"::equalsIgnoreCase)) {
            return REU;
        }
        if (lados.stream().anyMatch("Active"::equalsIgnoreCase)) {
            return AUTOR;
        }
        if (!lados.isEmpty()) {
            return TERCEIRO;
        }
        return DESCONHECIDO;
    }

    // ── Detalhe de um processo (/requests por CNJ + judit_ia) ─────────────────────────────────────

    public record Parte(String nome, String polo, String tipo, boolean auxiliar) {
    }

    public record Movimentacao(LocalDate data, String texto) {
    }

    /** O processo como veio do tribunal: com status e fase (que a base histórica não traz) e todos os andamentos. */
    public record ProcessoDetalhado(
            String status,
            String fase,
            String situacao,
            String autuacao,
            String classe,
            List<String> assuntos,
            BigDecimal valor,
            LocalDate distribuidoEm,
            String tribunal,
            List<String> orgaos,
            String juiz,
            List<Integer> instancias,
            List<Parte> partes,
            List<Movimentacao> movimentacoes,
            int anexos) {
    }

    /** @param resumoIa resumo da Judit em markdown, sem o HTML que vem junto; nulo se não veio */
    public record Detalhe(String resumoIa, ProcessoDetalhado processo) {
    }

    /**
     * Lê o {@code GET /responses} do pedido: os itens {@code lawsuit} (um por instância) e o
     * {@code summary} da IA, todos em {@code page_data}.
     */
    public static Detalhe detalhe(JsonNode respostas) {
        List<JsonNode> instancias = new ArrayList<>();
        List<String> resumos = new ArrayList<>();
        for (JsonNode item : respostas.path("page_data")) {
            String tipo = item.path("response_type").asText("");
            JsonNode dados = item.path("response_data");
            if ("lawsuit".equals(tipo)) {
                instancias.add(dados);
            } else if ("summary".equals(tipo)) {
                dados.path("data").forEach(texto -> resumos.add(texto.asText()));
            }
        }
        String resumo = resumos.isEmpty() ? null : resumoEmMarkdown(String.join("\n\n", resumos));
        return new Detalhe(resumo, instancias.isEmpty() ? null : processoDetalhado(instancias));
    }

    private static ProcessoDetalhado processoDetalhado(List<JsonNode> linhas) {
        List<JsonNode> ordenadas = linhas.stream()
                .sorted(Comparator.comparingInt(l -> l.path("instance").asInt(Integer.MAX_VALUE)))
                .toList();
        JsonNode capa = ordenadas.get(0);

        Set<Integer> instancias = new TreeSet<>();
        Set<String> assuntos = new LinkedHashSet<>();
        Set<String> orgaos = new LinkedHashSet<>();
        Map<String, Parte> partes = new LinkedHashMap<>();
        Map<String, Movimentacao> movimentacoes = new LinkedHashMap<>();
        int anexos = 0;
        LocalDate distribuidoEm = null;

        for (JsonNode linha : ordenadas) {
            if (linha.has("instance")) {
                instancias.add(linha.path("instance").asInt());
            }
            linha.path("subjects").forEach(a -> {
                String nome = texto(a.path("name"));
                if (nome != null) {
                    assuntos.add(nome);
                }
            });
            String orgao = texto(linha.path("county"));
            if (orgao != null) {
                orgaos.add(orgao);
            }
            linha.path("courts").forEach(c -> {
                String nome = texto(c.path("name"));
                if (nome != null) {
                    orgaos.add(nome);
                }
            });
            for (JsonNode parte : linha.path("parties")) {
                String nome = texto(parte.path("name"));
                if (nome == null) {
                    continue;
                }
                String polo = poloDaParte(texto(parte.path("side")));
                partes.putIfAbsent(nome + "|" + polo,
                        new Parte(nome, polo, texto(parte.path("person_type")), auxiliar(parte)));
            }
            for (JsonNode passo : linha.path("steps")) {
                String chave = texto(passo.path("step_id"));
                Movimentacao movimentacao = new Movimentacao(data(passo.path("step_date")), texto(passo.path("content")));
                movimentacoes.putIfAbsent(chave != null ? chave : movimentacao.data() + "|" + movimentacao.texto(), movimentacao);
            }
            anexos += linha.path("attachments").size();
            distribuidoEm = menor(distribuidoEm, data(linha.path("distribution_date")));
        }

        // Partes de verdade primeiro, ativo antes de passivo; advogados e peritos no fim.
        List<Parte> partesOrdenadas = partes.values().stream()
                .sorted(Comparator.comparing(Parte::auxiliar).thenComparing(p -> ordemDoPolo(p.polo())))
                .toList();
        List<Movimentacao> andamentos = movimentacoes.values().stream()
                .sorted(Comparator.comparing(Movimentacao::data, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        JsonNode classes = capa.path("classifications");
        BigDecimal valor = capa.path("amount").isNumber() && capa.path("amount").decimalValue().signum() > 0
                ? capa.path("amount").decimalValue()
                : null;
        return new ProcessoDetalhado(
                texto(capa.path("status")),
                texto(capa.path("phase")),
                texto(capa.path("situation")),
                texto(capa.path("name")),
                classes.isArray() && !classes.isEmpty() ? texto(classes.get(0).path("name")) : null,
                List.copyOf(assuntos),
                valor,
                distribuidoEm,
                texto(capa.path("tribunal_acronym")),
                List.copyOf(orgaos),
                texto(capa.path("judge")),
                List.copyOf(instancias),
                partesOrdenadas,
                andamentos,
                anexos);
    }

    private static String poloDaParte(String lado) {
        if ("Active".equalsIgnoreCase(lado)) {
            return "ATIVO";
        }
        if ("Passive".equalsIgnoreCase(lado)) {
            return "PASSIVO";
        }
        if ("Interested".equalsIgnoreCase(lado)) {
            return "INTERESSADO";
        }
        return "OUTRO";
    }

    private static int ordemDoPolo(String polo) {
        return switch (polo) {
            case "ATIVO" -> 0;
            case "PASSIVO" -> 1;
            case "INTERESSADO" -> 2;
            default -> 3;
        };
    }

    private static final Pattern ITEM = Pattern.compile("<li[^>]*>(.*?)</li>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGRITO = Pattern.compile("<strong[^>]*>(.*?)</strong>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TRECHO = Pattern.compile("<span[^>]*>(.*?)</span>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern QUEBRA = Pattern.compile("<br\\s*/?>|</?p[^>]*>|</?ul[^>]*>|</?ol[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ETIQUETA = Pattern.compile("<[^>]+>");

    /**
     * O resumo da Judit é markdown com HTML do site deles no meio ({@code <strong className="...">}).
     * Vira markdown puro: negrito vira {@code **}, cada item de lista vira uma linha só
     * ("**Nome** *papel* descrição") e o resto das etiquetas sai. A tela mostra com react-markdown,
     * que não executa HTML — o que sobrar aparece como texto, nunca como código.
     */
    static String resumoEmMarkdown(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String texto = bruto.replace("\r", "");
        texto = ITEM.matcher(texto).replaceAll(m -> Matcher.quoteReplacement("\n- " + itemEmUmaLinha(m.group(1)) + "\n"));
        texto = negrito(texto);
        texto = TRECHO.matcher(texto).replaceAll(m -> Matcher.quoteReplacement(m.group(1)));
        texto = QUEBRA.matcher(texto).replaceAll("\n\n");
        texto = ETIQUETA.matcher(texto).replaceAll("");
        // Sem recuo: quatro espaços no começo da linha viram bloco de código no markdown.
        texto = texto.lines().map(String::strip).collect(Collectors.joining("\n"));
        // Lista colada: tira a linha em branco entre itens.
        texto = texto.replaceAll("\n{3,}", "\n\n").replaceAll("(?m)^(- .*)\n\n(?=- )", "$1\n");
        return texto.strip();
    }

    private static String itemEmUmaLinha(String item) {
        String linha = negrito(item);
        linha = TRECHO.matcher(linha).replaceAll(m -> Matcher.quoteReplacement("*" + m.group(1).strip() + "*"));
        linha = ETIQUETA.matcher(linha).replaceAll(" ");
        return linha.replaceAll("\\s+", " ").strip();
    }

    private static String negrito(String texto) {
        return NEGRITO.matcher(texto).replaceAll(m -> {
            String dentro = ETIQUETA.matcher(m.group(1)).replaceAll("").replaceAll("\\s+", " ").strip();
            return Matcher.quoteReplacement(dentro.isEmpty() ? "" : "**" + dentro + "**");
        });
    }

    // ── Utilitários ──────────────────────────────────────────────────────────────────────────────

    private static String texto(JsonNode no) {
        if (no == null || no.isMissingNode() || no.isNull()) {
            return null;
        }
        String valor = no.asText().trim();
        return valor.isEmpty() || NAO_INFORMADO.equalsIgnoreCase(valor) ? null : valor;
    }

    /**
     * CNPJ casa pela raiz: matriz e filiais são a mesma pessoa jurídica, e o processo pode ter sido
     * aberto contra qualquer estabelecimento. CPF casa inteiro.
     */
    static boolean mesmaPessoa(String documento, String daParte) {
        if (documento == null || daParte == null) {
            return false;
        }
        if (documento.length() == 14 && daParte.length() == 14) {
            return documento.regionMatches(0, daParte, 0, 8);
        }
        return documento.equals(daParte);
    }

    private static String digitos(String valor) {
        return valor == null ? null : valor.replaceAll("\\D", "");
    }

    private static LocalDate data(JsonNode no) {
        String valor = texto(no);
        if (valor == null) {
            return null;
        }
        try {
            return Instant.parse(valor).atZone(FUSO).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(valor.length() >= 10 ? valor.substring(0, 10) : valor);
            } catch (DateTimeParseException ignorada) {
                return null;
            }
        }
    }

    private static LocalDate menor(LocalDate atual, LocalDate nova) {
        if (nova == null) {
            return atual;
        }
        return atual == null || nova.isBefore(atual) ? nova : atual;
    }

    private static LocalDate maior(LocalDate atual, LocalDate nova) {
        if (nova == null) {
            return atual;
        }
        return atual == null || nova.isAfter(atual) ? nova : atual;
    }
}
