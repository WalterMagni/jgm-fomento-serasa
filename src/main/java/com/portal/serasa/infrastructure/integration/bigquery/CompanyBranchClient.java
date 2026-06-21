package com.portal.serasa.infrastructure.integration.bigquery;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.FieldValueList;
import com.google.cloud.bigquery.QueryParameterValue;
import com.google.cloud.bigquery.QueryJobConfiguration;
import com.google.cloud.bigquery.TableResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consulta os estabelecimentos (matriz + filiais) de uma raiz de CNPJ no
 * dataset público da Receita Federal hospedado no Base dos Dados (BigQuery).
 *
 * <p>A tabela guarda vários snapshots históricos; deduplicamos pelo snapshot
 * mais recente de cada CNPJ ({@code ROW_NUMBER ... ORDER BY data DESC}) e
 * filtramos só estabelecimentos ativos ({@code situacao_cadastral = '2'}).
 * O JOIN com o diretório de municípios traz o nome da cidade para a
 * geocodificação por centroide.</p>
 */
@Slf4j
@Component
public class CompanyBranchClient {

    private static final String QUERY = """
            SELECT e.cnpj,
                   e.identificador_matriz_filial,
                   e.nome_fantasia,
                   e.sigla_uf,
                   e.id_municipio,
                   m.nome AS nome_municipio,
                   e.logradouro,
                   e.numero,
                   e.bairro,
                   e.cep
            FROM (
              SELECT *,
                ROW_NUMBER() OVER (PARTITION BY cnpj ORDER BY data DESC) AS rn
              FROM `basedosdados.br_me_cnpj.estabelecimentos`
              WHERE cnpj_basico = @raiz
            ) e
            LEFT JOIN `basedosdados.br_bd_diretorios_brasil.municipio` m
              ON e.id_municipio = m.id_municipio
            WHERE e.rn = 1
              AND e.situacao_cadastral = '2'
            """;

    private final ObjectProvider<BigQuery> bigQueryProvider;

    public CompanyBranchClient(ObjectProvider<BigQuery> bigQueryProvider) {
        this.bigQueryProvider = bigQueryProvider;
    }

    public boolean isAvailable() {
        return bigQueryProvider.getIfAvailable() != null;
    }

    public List<BranchRow> fetchBranches(String cnpjRaiz) {
        BigQuery bigQuery = bigQueryProvider.getIfAvailable();
        if (bigQuery == null) {
            throw new IllegalStateException("BigQuery não está habilitado (bigquery.enabled=false)");
        }
        QueryJobConfiguration config = QueryJobConfiguration.newBuilder(QUERY)
                .addNamedParameter("raiz", QueryParameterValue.string(cnpjRaiz))
                .setUseLegacySql(false)
                .build();
        try {
            TableResult result = bigQuery.query(config);
            List<BranchRow> rows = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                rows.add(new BranchRow(
                        str(row, "cnpj"),
                        str(row, "identificador_matriz_filial"),
                        str(row, "nome_fantasia"),
                        str(row, "sigla_uf"),
                        str(row, "id_municipio"),
                        str(row, "nome_municipio"),
                        str(row, "logradouro"),
                        str(row, "numero"),
                        str(row, "bairro"),
                        str(row, "cep")));
            }
            log.info("BigQuery filiais raiz={} retornou {} estabelecimentos ativos", cnpjRaiz, rows.size());
            return rows;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Consulta BigQuery interrompida", e);
        }
    }

    private static String str(FieldValueList row, String field) {
        var value = row.get(field);
        return value == null || value.isNull() ? null : value.getStringValue();
    }

    public record BranchRow(
            String cnpj,
            String matrizFilial,
            String nomeFantasia,
            String uf,
            String idMunicipio,
            String nomeMunicipio,
            String logradouro,
            String numero,
            String bairro,
            String cep) {
    }
}
