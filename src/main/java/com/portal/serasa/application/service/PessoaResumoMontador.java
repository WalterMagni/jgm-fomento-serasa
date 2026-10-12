package com.portal.serasa.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.portal.serasa.application.port.out.UltimaAnalisePessoa;
import com.portal.serasa.domain.model.pessoa.PessoaResumo;
import com.portal.serasa.domain.model.pessoa.SituacaoPessoa;
import com.portal.serasa.domain.model.pessoa.TipoRestricao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta o resumo de uma pessoa a partir da última análise recortada pelo repositório.
 *
 * <p>Separado do serviço para a regra de soma e de situação ser testada sem banco.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PessoaResumoMontador {

    private final ObjectMapper objectMapper;

    /**
     * @param razaoSocialPorCnpj empresas da carteira, por CNPJ só dígitos; sócio fora dela não
     *                           vira vínculo
     */
    public PessoaResumo montar(UltimaAnalisePessoa analise, Map<String, String> razaoSocialPorCnpj) {
        JsonNode resumos = ler(analise.restricoesJson());
        List<PessoaResumo.Restricao> restricoes = new ArrayList<>();
        int total = 0;
        BigDecimal divida = BigDecimal.ZERO;
        for (TipoRestricao tipo : TipoRestricao.values()) {
            JsonNode resumo = resumos.path(tipo.chave());
            int quantidade = resumo.path("count").asInt(0);
            if (quantidade <= 0) {
                continue;
            }
            JsonNode saldo = resumo.path("balance");
            BigDecimal valor = (saldo.isNumber() ? saldo.decimalValue() : BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            restricoes.add(new PessoaResumo.Restricao(tipo, quantidade, valor));
            total += quantidade;
            divida = divida.add(valor);
        }

        List<PessoaResumo.Vinculo> vinculos = cnpjsDosSocios(analise.sociosJson()).stream()
                .filter(razaoSocialPorCnpj::containsKey)
                .map(cnpj -> new PessoaResumo.Vinculo(cnpj, razaoSocialPorCnpj.get(cnpj)))
                .toList();

        return new PessoaResumo(analise.cpf(), analise.nome(), analise.cidade(), analise.uf(),
                analise.consultaEm(), restricoes, total, divida, SituacaoPessoa.classificar(total, divida),
                vinculos);
    }

    /** CNPJs (só dígitos, sem repetir) das empresas em que a pessoa aparece como sócia. */
    public Set<String> cnpjsDosSocios(String sociosJson) {
        JsonNode socios = ler(sociosJson);
        Set<String> cnpjs = new LinkedHashSet<>();
        if (!socios.isArray()) {
            return cnpjs;
        }
        for (JsonNode empresa : socios) {
            String digitos = empresa.path("businessDocument").asText("").replaceAll("\\D", "");
            if (digitos.length() == 14) {
                cnpjs.add(digitos);
            }
        }
        return cnpjs;
    }

    private JsonNode ler(String json) {
        if (json == null || json.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            // Uma linha ruim não pode derrubar a lista inteira: a pessoa aparece sem restrições.
            log.warn("JSON inválido no resumo de pessoa física: {}", e.getOriginalMessage());
            return MissingNode.getInstance();
        }
    }
}
