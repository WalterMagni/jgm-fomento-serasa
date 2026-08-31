package com.portal.serasa.infrastructure.integration.cnpj;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consulta os estabelecimentos (matriz + filiais) de uma raiz de CNPJ na cópia
 * local do cadastro da Receita Federal carregada pelo
 * <a href="https://github.com/caiopizzol/cnpj-data-pipeline">cnpj-data-pipeline</a>
 * num Postgres dedicado.
 *
 * <p>Substitui a antiga consulta ao BigQuery (Base dos Dados), que ficou cara.
 * A conexão com o banco da Receita é do {@link ReceitaDataSourceProvider}, compartilhada
 * com os demais consumidores; se estiver indisponível, a consulta de filiais responde 503.</p>
 *
 * <p>A PK é {@code (cnpj_basico, cnpj_ordem, cnpj_dv)} — uma linha por
 * estabelecimento, sem snapshots históricos, então não há dedup. O CNPJ de 14
 * dígitos é a concatenação das três colunas. {@code municipio} é o código da
 * Receita (não IBGE); o JOIN com {@code municipios} traz o nome para a
 * geocodificação por centroide. Filtra só estabelecimentos ativos
 * ({@code situacao_cadastral = '02'}).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyBranchClient {

    private static final String QUERY = """
            SELECT e.cnpj_basico || e.cnpj_ordem || e.cnpj_dv AS cnpj,
                   e.identificador_matriz_filial             AS matriz_filial,
                   e.nome_fantasia,
                   e.uf,
                   e.municipio                                AS id_municipio,
                   m.descricao                                AS nome_municipio,
                   e.tipo_logradouro,
                   e.logradouro,
                   e.numero,
                   e.bairro,
                   e.cep
            FROM estabelecimentos e
            LEFT JOIN municipios m ON e.municipio = m.codigo
            WHERE e.cnpj_basico = ?
              AND e.situacao_cadastral IN ('02', '2')
            """;

    private final ReceitaDataSourceProvider receita;

    /** Teto de linhas por raiz — blindagem contra raiz inválida que varra a tabela. */
    @Value("${cnpj.branches.max-rows:2000}")
    private int maxRows;

    public boolean isAvailable() {
        return receita.isAvailable();
    }

    public List<BranchRow> fetchBranches(String cnpjRaiz) {
        if (!isAvailable()) {
            throw new IllegalStateException("Consulta de filiais indisponivel no momento");
        }
        try {
            List<BranchRow> rows = receita.template(maxRows).query(QUERY, (rs, i) -> new BranchRow(
                    rs.getString("cnpj"),
                    matrizFilial(rs.getObject("matriz_filial")),
                    rs.getString("nome_fantasia"),
                    rs.getString("uf"),
                    rs.getString("id_municipio"),
                    rs.getString("nome_municipio"),
                    joinLogradouro(rs.getString("tipo_logradouro"), rs.getString("logradouro")),
                    rs.getString("numero"),
                    rs.getString("bairro"),
                    rs.getString("cep")), cnpjRaiz);
            log.info("Filiais (Receita) raiz={} retornou {} estabelecimentos ativos", cnpjRaiz, rows.size());
            return rows;
        } catch (RuntimeException ex) {
            receita.reset();
            throw new IllegalStateException("Consulta de filiais indisponivel no momento", ex);
        }
    }

    /** identificador_matriz_filial vem como INTEGER (1=matriz, 2=filial) → "1"/"2". */
    private static String matrizFilial(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String joinLogradouro(String tipo, String logradouro) {
        String t = tipo == null ? "" : tipo.trim();
        String l = logradouro == null ? "" : logradouro.trim();
        String joined = (t + " " + l).trim();
        return joined.isEmpty() ? null : joined;
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
