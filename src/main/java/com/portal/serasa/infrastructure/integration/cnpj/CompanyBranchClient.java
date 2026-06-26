package com.portal.serasa.infrastructure.integration.cnpj;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Consulta os estabelecimentos (matriz + filiais) de uma raiz de CNPJ na cópia
 * local do cadastro da Receita Federal carregada pelo
 * <a href="https://github.com/caiopizzol/cnpj-data-pipeline">cnpj-data-pipeline</a>
 * num Postgres dedicado.
 *
 * <p>Substitui a antiga consulta ao BigQuery (Base dos Dados), que ficou cara.
 * O banco da Receita roda separado do banco da aplicação, então este client é
 * dono do próprio pool Hikari (read-only), criado em {@link #init()} só quando
 * {@code cnpj.datasource.enabled=true}. Sem isso o app sobe normal e a consulta
 * de filiais fica indisponível (503), igual ao tratamento do Gemini.</p>
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

    @Value("${cnpj.datasource.enabled:false}")
    private boolean enabled;

    @Value("${cnpj.datasource.url:}")
    private String url;

    @Value("${cnpj.datasource.username:}")
    private String username;

    @Value("${cnpj.datasource.password:}")
    private String password;

    /** Teto de linhas por raiz — blindagem contra raiz inválida que varra a tabela. */
    @Value("${cnpj.branches.max-rows:2000}")
    private int maxRows;

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    void init() {
        if (!enabled || url == null || url.isBlank()) {
            log.info("Consulta de filiais desabilitada (cnpj.datasource.enabled=false ou url vazia)");
            return;
        }
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setReadOnly(true);
        ds.setMaximumPoolSize(3);
        ds.setPoolName("cnpj-receita-pool");
        this.dataSource = ds;
        JdbcTemplate template = new JdbcTemplate(ds);
        template.setMaxRows(maxRows);
        this.jdbcTemplate = template;
        log.info("Consulta de filiais habilitada (Postgres CNPJ Receita: {})", url);
    }

    @PreDestroy
    void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    public boolean isAvailable() {
        return jdbcTemplate != null;
    }

    public List<BranchRow> fetchBranches(String cnpjRaiz) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("Consulta de filiais indisponível (cnpj.datasource.enabled=false)");
        }
        List<BranchRow> rows = jdbcTemplate.query(QUERY, (rs, i) -> new BranchRow(
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
