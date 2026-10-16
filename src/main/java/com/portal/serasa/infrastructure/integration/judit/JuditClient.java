package com.portal.serasa.infrastructure.integration.judit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.portal.serasa.domain.exception.ExternalApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Supplier;

/**
 * Rotas da Judit. As síncronas (base histórica, sem esperar tribunal) respondem na hora; o detalhe
 * de processo é assíncrono: pede, acompanha a situação e busca as respostas. Só os pedidos cobram.
 *
 * <p>O formato do corpo é o que funcionou nos testes de setembro ({@code scripts/judit-probe.sh}):
 * tudo dentro de {@code search}, filtros em {@code search.search_params}. A coleção do Postman que a
 * Judit mandou põe a paginação fora de {@code search} — não usamos paginação ainda; ver
 * docs/judit/02-implementacao.md.</p>
 */
@Slf4j
@Component
public class JuditClient {

    public static final String PROVIDER = "Judit";

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

    /** Agregados: total de processos e contagens por polo, tribunal, área, classe, assunto e fase. */
    public JsonNode resumo(String documento) {
        return post("/lawsuits/synthetic", corpo(documento, null));
    }

    /**
     * Os processos (capa + último andamento). A rota devolve no máximo 1.000, sem avisar; quem chama
     * compara com o total do resumo.
     *
     * @param polo {@code "Passive"} para só os processos em que o documento é réu; nulo traz todos
     */
    public JsonNode processos(String documento, String polo) {
        return post("/lawsuits", corpo(documento, polo));
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

    /** As respostas de um pedido concluído: o processo (uma por instância) e o resumo de IA. Não cobra. */
    public JsonNode respostasDoPedido(String requestId) {
        return chamar("/responses", () -> requestsClient.get()
                .uri(uri -> uri.path("/responses")
                        .queryParam("request_id", requestId)
                        .queryParam("page", 1)
                        .queryParam("page_size", 100)
                        .build())
                .retrieve()
                .body(JsonNode.class));
    }

    private ObjectNode corpo(String documento, String polo) {
        ObjectNode corpo = objectMapper.createObjectNode();
        ObjectNode search = corpo.putObject("search");
        search.put("search_type", documento.length() == 11 ? "cpf" : "cnpj");
        search.put("search_key", documento);
        if (polo != null) {
            search.putObject("search_params").putObject("filter").put("side", polo);
        }
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
