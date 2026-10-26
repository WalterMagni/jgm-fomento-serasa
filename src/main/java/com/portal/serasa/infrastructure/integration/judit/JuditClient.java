package com.portal.serasa.infrastructure.integration.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.serasa.domain.exception.ExternalApiException;
import com.portal.serasa.domain.model.PoloJudit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Rotas da Judit. As síncronas (base histórica, sem esperar tribunal) respondem na hora; o detalhe
 * de processo e o On Demand são assíncronos: pede, acompanha a situação e busca as respostas. Só os
 * pedidos cobram.
 *
 * <p>O formato do corpo é o que funcionou nos testes de setembro ({@code scripts/judit-probe.sh}) e de
 * outubro: tudo dentro de {@code search}, filtros em {@code search.search_params}. Ver
 * docs/judit/03-on-demand.md.</p>
 */
@Slf4j
@Component
public class JuditClient {

    public static final String PROVIDER = "Judit";

    /** Itens por página no {@code /responses}. Ler as páginas não cobra. */
    private static final int TAMANHO_DA_PAGINA = 100;

    private final RestClient restClient;
    private final RestClient requestsClient;
    private final ObjectMapper objectMapper;
    private final JuditProperties properties;

    public JuditClient(@Qualifier("juditRestClient") RestClient restClient,
                       @Qualifier("juditRequestsRestClient") RestClient requestsClient,
                       ObjectMapper objectMapper,
                       JuditProperties properties) {
        this.restClient = restClient;
        this.requestsClient = requestsClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * A pré-consulta: total de processos e contagens por polo, tribunal, justiça, área, classe, assunto
     * e fase, na base histórica. A lista vem pelo On Demand.
     */
    public JsonNode resumo(String documento) {
        return post("/lawsuits/synthetic", corpo(documento));
    }

    /**
     * Pede o detalhe de um processo: consulta processual por CNJ, no tribunal, com resumo de IA.
     * Sem anexos, que têm cobrança à parte. Devolve o {@code request_id} para acompanhar.
     */
    public String pedirDetalhe(String cnj) {
        ObjectNode corpo = objectMapper.createObjectNode();
        ObjectNode search = corpo.putObject("search");
        search.put("search_type", "lawsuit_cnj");
        search.put("search_key", cnj);
        corpo.put("with_attachments", false);
        corpo.putArray("judit_ia").add("summary");
        return pedir(corpo);
    }

    /**
     * Pede os processos do documento num polo, no Data Lake e nos tribunais ao vivo. Sem
     * {@code on_demand: true} a Judit responde só do cache dela — visto no teste de 2026-10-26, em que o
     * pedido voltou com {@code cached: true} e sem andamentos. Devolve o {@code request_id}.
     */
    public String pedirOnDemand(String documento, PoloJudit polo) {
        ObjectNode corpo = objectMapper.createObjectNode();
        ObjectNode search = corpo.putObject("search");
        search.put("search_type", documento.length() == 11 ? "cpf" : "cnpj");
        search.put("search_key", documento);
        search.put("on_demand", true);
        search.put("response_type", "lawsuits");
        search.putObject("search_params").putObject("filter").put("side", polo.lado());
        corpo.put("with_attachments", false);
        return pedir(corpo);
    }

    private String pedir(ObjectNode corpo) {
        JsonNode resposta = chamar("/requests", () -> requestsClient.post().uri("/requests").body(corpo).retrieve().body(JsonNode.class));
        String requestId = resposta.path("request_id").asText(null);
        if (requestId == null || requestId.isBlank()) {
            throw new ExternalApiException(PROVIDER, 502, "A Judit aceitou o pedido mas não devolveu o número dele.", null);
        }
        return requestId;
    }

    /** Situação do pedido na Judit: {@code pending}, {@code completed}... Consultar não cobra. */
    public String situacaoDoPedido(String requestId) {
        JsonNode resposta = chamar("/requests/{id}",
                () -> requestsClient.get().uri("/requests/{id}", requestId).retrieve().body(JsonNode.class));
        return resposta.path("status").asText("");
    }

    /**
     * As respostas de um pedido concluído, todas as páginas juntas no {@code page_data} da primeira:
     * no detalhe, o processo (uma por instância) e o resumo de IA; no On Demand, um processo por item.
     * Não cobra.
     */
    public JsonNode respostasDoPedido(String requestId) {
        JsonNode primeira = paginaDeRespostas(requestId, 1);
        int paginas = primeira.path("all_pages_count").asInt(1);
        if (paginas > 1 && primeira.path("page_data") instanceof ArrayNode itens) {
            for (int pagina = 2; pagina <= paginas; pagina++) {
                paginaDeRespostas(requestId, pagina).path("page_data").forEach(itens::add);
            }
        }
        return primeira;
    }

    private JsonNode paginaDeRespostas(String requestId, int pagina) {
        return chamar("/responses", () -> requestsClient.get()
                .uri(uri -> uri.path("/responses")
                        .queryParam("request_id", requestId)
                        .queryParam("page", pagina)
                        .queryParam("page_size", TAMANHO_DA_PAGINA)
                        .build())
                .retrieve()
                .body(JsonNode.class));
    }

    private ObjectNode corpo(String documento) {
        ObjectNode corpo = objectMapper.createObjectNode();
        ObjectNode search = corpo.putObject("search");
        search.put("search_type", documento.length() == 11 ? "cpf" : "cnpj");
        search.put("search_key", documento);
        return corpo;
    }

    private JsonNode post(String rota, ObjectNode corpo) {
        return chamar(rota, () -> restClient.post().uri(rota).body(corpo).retrieve().body(JsonNode.class));
    }

    private JsonNode chamar(String rota, Supplier<JsonNode> chamada) {
        if (!properties.configurada()) {
            throw new ExternalApiException(PROVIDER, 503,
                    "A chave da Judit não está configurada no servidor (JUDIT_API_KEY). Avise o suporte.", null);
        }
        try {
            JsonNode resposta = chamada.get();
            if (resposta == null || resposta.isNull()) {
                throw new ExternalApiException(PROVIDER, 502, "A Judit devolveu uma resposta vazia.", null);
            }
            return resposta;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Judit {} respondeu {}: {}", rota, status, e.getResponseBodyAsString());
            throw new ExternalApiException(PROVIDER, statusParaTela(status), mensagem(status), e);
        } catch (ResourceAccessException e) {
            throw new ExternalApiException(PROVIDER, 0, "A Judit não respondeu (tempo esgotado). Tente novamente.", e);
        }
    }

    /**
     * 401 e 403 da Judit são problema da nossa chave, não do usuário logado. Repassar 401 faria a tela
     * achar que a sessão do portal expirou.
     */
    private static int statusParaTela(int status) {
        return status == 401 || status == 403 ? 502 : status;
    }

    private static String mensagem(int status) {
        return switch (status) {
            case 400 -> "A Judit recusou a consulta (documento ou parâmetro inválido).";
            case 401, 403 -> "A Judit recusou a chave de acesso. Avise o suporte para conferir a JUDIT_API_KEY.";
            case 404 -> "A Judit não encontrou nada para este documento.";
            case 429 -> "Limite de consultas por minuto da Judit atingido. Tente novamente em instantes.";
            default -> status >= 500
                    ? "A Judit está fora do ar no momento (erro " + status + ")."
                    : "Falha na consulta à Judit (erro " + status + ").";
        };
    }
}
