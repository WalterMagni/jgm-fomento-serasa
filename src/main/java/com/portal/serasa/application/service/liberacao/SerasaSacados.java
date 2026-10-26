package com.portal.serasa.application.service.liberacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.portal.serasa.domain.model.pessoa.PessoaResumo;
import com.portal.serasa.domain.model.pessoa.TipoRestricao;
import com.portal.serasa.infrastructure.persistence.entity.CreditAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.entity.PersonAnalysisEntity;
import com.portal.serasa.infrastructure.persistence.repository.CreditAnalysisJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.PersonAnalysisJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A última consulta Serasa de cada sacado, resumida para a esteira (pedido do time em
 * 2026-10-26): quantas restrições, quanto somam e quando foi a consulta. Empresa pela consulta PJ
 * ({@code credit_analysis}), pessoa física pela PF ({@code person_analysis}).
 *
 * <p>Só lê o que já foi consultado — não dispara consulta, que tem custo. Sempre a consulta mais
 * recente, mesmo em card já decidido. A conta é a da carteira e da página da empresa: os sete
 * tipos de {@link TipoRestricao}, Ação judicial e Falência vindo dos fatos.</p>
 */
@Component
@RequiredArgsConstructor
public class SerasaSacados {

    /** Ação judicial e falência ficam nos fatos da consulta; o resto, nas restrições. */
    private static final Set<TipoRestricao> DOS_FATOS = EnumSet.of(TipoRestricao.ACAO_JUDICIAL, TipoRestricao.FALENCIA);

    private final CreditAnalysisJpaRepository creditAnalysisRepository;
    private final PersonAnalysisJpaRepository personAnalysisRepository;

    /** Sem restrição ({@code ocorrencias == 0}) é "Nada consta". */
    public record Resumo(LocalDateTime consultaEm, int ocorrencias, BigDecimal divida,
                         List<PessoaResumo.Restricao> restricoes) {
    }

    /** Por documento (só dígitos); quem nunca foi consultado fica de fora do mapa. */
    @Transactional(readOnly = true)
    public Map<String, Resumo> porDocumento(Collection<String> documentos) {
        List<String> cnpjs = documentos.stream().filter(documento -> documento != null && documento.length() == 14).distinct().toList();
        List<String> cpfs = documentos.stream().filter(documento -> documento != null && documento.length() == 11).distinct().toList();
        Map<String, Resumo> resumos = new HashMap<>();
        if (!cnpjs.isEmpty()) {
            for (CreditAnalysisEntity analise : creditAnalysisRepository.findLatestByCnpjIn(cnpjs)) {
                resumos.put(analise.getCnpj(), resumir(analise.getConsultaEm(), analise.getNegativeSummary(), analise.getInquiryHistory()));
            }
        }
        if (!cpfs.isEmpty()) {
            // Duas consultas no mesmo instante: fica a de maior id, como na lista de Pessoas Físicas.
            personAnalysisRepository.findLatestByCpfIn(cpfs).stream()
                    .sorted(Comparator.comparing(PersonAnalysisEntity::getId))
                    .forEach(analise -> resumos.put(analise.getCpf(),
                            resumir(analise.getConsultaEm(), analise.getNegativeSummary(), analise.getFacts())));
        }
        return resumos;
    }

    static Resumo resumir(LocalDateTime consultaEm, JsonNode negativos, JsonNode fatos) {
        List<PessoaResumo.Restricao> restricoes = new ArrayList<>();
        int ocorrencias = 0;
        BigDecimal divida = BigDecimal.ZERO;
        for (TipoRestricao tipo : TipoRestricao.values()) {
            JsonNode secao = DOS_FATOS.contains(tipo) ? fatos : negativos;
            JsonNode resumo = (secao == null ? MissingNode.getInstance() : secao).path(tipo.chave()).path("summary");
            int quantidade = resumo.path("count").asInt(0);
            if (quantidade <= 0) {
                continue;
            }
            JsonNode saldo = resumo.path("balance");
            BigDecimal valor = (saldo.isNumber() ? saldo.decimalValue() : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            restricoes.add(new PessoaResumo.Restricao(tipo, quantidade, valor));
            ocorrencias += quantidade;
            divida = divida.add(valor);
        }
        return new Resumo(consultaEm, ocorrencias, divida, List.copyOf(restricoes));
    }
}
