package com.portal.serasa.infrastructure.integration.cnpj;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Consulta o quadro societário na cópia local do cadastro da Receita Federal
 * (tabela {@code socios}, ~27M linhas).
 *
 * <p>Duas direções:</p>
 * <ul>
 *   <li>{@link #fetchShareholders(String)} — sócios de uma raiz de CNPJ.</li>
 *   <li>{@link #fetchCompaniesOfShareholder(String, String)} — todas as empresas de um sócio,
 *       que é o mesmo dado vendido como "empresas vinculadas ao CPF" por consultas pagas.</li>
 * </ul>
 *
 * <p><b>Máscara do CPF.</b> A Receita publica o CPF do sócio como {@code ***216508**}, ou seja
 * os dígitos 4..9 do CPF. É determinístico: dado o CPF completo, a máscara é derivável por
 * {@code '***' || substring(cpf,4,6) || '**'} (ver {@code CpfMask}). Como os dois lados da
 * comparação saem da mesma fonte, a máscara funciona como chave de cruzamento entre empresas
 * mesmo sem nunca conhecer o CPF completo. Só que 6 dígitos colidem, então o nome sempre entra
 * no JOIN junto com a máscara.</p>
 *
 * <p>Sócio pessoa jurídica ({@code identificador_de_socio = '1'}) traz o CNPJ completo de 14
 * dígitos, sem máscara — nesse caso o match é exato.</p>
 *
 * <p>A busca reversa (por sócio) depende do índice {@code idx_socios_cpf_nome} sobre
 * {@code (cnpj_cpf_do_socio, nome_socio)}. Sem ele a consulta varre as 27M linhas.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShareholderQueryClient {

    /** Sócios de uma raiz de CNPJ. */
    private static final String SHAREHOLDERS_QUERY = """
            SELECT s.identificador_de_socio,
                   s.nome_socio,
                   s.cnpj_cpf_do_socio,
                   s.qualificacao_do_socio,
                   q.descricao AS qualificacao_descricao,
                   s.data_entrada_sociedade,
                   s.faixa_etaria,
                   e.razao_social,
                   est.situacao_cadastral,
                   est.cnpj_basico || est.cnpj_ordem || est.cnpj_dv AS company_cnpj
            FROM socios s
            LEFT JOIN qualificacoes_socios q ON q.codigo = s.qualificacao_do_socio
            LEFT JOIN empresas e ON e.cnpj_basico = s.cnpj_basico
            LEFT JOIN estabelecimentos est ON est.cnpj_basico = s.cnpj_basico
                                          AND est.identificador_matriz_filial IN (1, '1')
            WHERE s.cnpj_basico = ?
            """;

    /**
     * Empresas de um sócio, casando por documento + nome. Traz a razão social e a situação
     * cadastral da matriz para que a listagem não precise de uma segunda ida ao banco.
     */
    private static final String COMPANIES_QUERY = """
            SELECT s.cnpj_basico,
                   e.razao_social,
                   s.qualificacao_do_socio,
                   q.descricao AS qualificacao_descricao,
                   s.data_entrada_sociedade,
                   est.situacao_cadastral,
                   est.cnpj_basico || est.cnpj_ordem || est.cnpj_dv AS company_cnpj
            FROM socios s
            LEFT JOIN empresas e ON e.cnpj_basico = s.cnpj_basico
            LEFT JOIN qualificacoes_socios q ON q.codigo = s.qualificacao_do_socio
            LEFT JOIN estabelecimentos est ON est.cnpj_basico = s.cnpj_basico
                                          AND est.identificador_matriz_filial IN (1, '1')
            WHERE s.cnpj_cpf_do_socio = ?
              AND s.nome_socio = ?
            """;

    private final ReceitaDataSourceProvider receita;

    /** Teto de linhas por consulta — sócio de holding grande pode retornar milhares. */
    @Value("${cnpj.shareholders.max-rows:500}")
    private int maxRows;

    public boolean isAvailable() {
        return receita.isAvailable();
    }

    public List<ShareholderRow> fetchShareholders(String cnpjRaiz) {
        return query(SHAREHOLDERS_QUERY, SHAREHOLDER_MAPPER, "socios raiz=" + cnpjRaiz, cnpjRaiz);
    }

    /**
     * @param documentMask CPF mascarado ({@code ***216508**}) ou CNPJ completo, conforme guardado
     *                     em {@code socios.cnpj_cpf_do_socio}.
     */
    public List<ShareholderCompanyRow> fetchCompaniesOfShareholder(String documentMask, String name) {
        return query(COMPANIES_QUERY, COMPANY_MAPPER, "empresas do socio " + name, documentMask, name);
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, String description, Object... args) {
        if (!isAvailable()) {
            throw new IllegalStateException("Consulta de quadro societario indisponivel no momento");
        }
        try {
            List<T> rows = receita.template(maxRows).query(sql, mapper, args);
            log.debug("Quadro societario (Receita) {} retornou {} linhas", description, rows.size());
            return rows;
        } catch (RuntimeException ex) {
            receita.reset();
            throw new IllegalStateException("Consulta de quadro societario indisponivel no momento", ex);
        }
    }

    private static final RowMapper<ShareholderRow> SHAREHOLDER_MAPPER = (rs, i) -> new ShareholderRow(
            documentType(rs.getString("identificador_de_socio")),
            rs.getString("nome_socio"),
            rs.getString("cnpj_cpf_do_socio"),
            rs.getString("qualificacao_do_socio"),
            rs.getString("qualificacao_descricao"),
            toLocalDate(rs.getDate("data_entrada_sociedade")),
            rs.getString("faixa_etaria"),
            rs.getString("razao_social"),
            rs.getString("situacao_cadastral"),
            rs.getString("company_cnpj"));

    private static final RowMapper<ShareholderCompanyRow> COMPANY_MAPPER = (rs, i) -> new ShareholderCompanyRow(
            rs.getString("cnpj_basico"),
            rs.getString("razao_social"),
            rs.getString("qualificacao_do_socio"),
            rs.getString("qualificacao_descricao"),
            toLocalDate(rs.getDate("data_entrada_sociedade")),
            rs.getString("situacao_cadastral"),
            rs.getString("company_cnpj"));

    /** identificador_de_socio: 1=pessoa jurídica, 2=pessoa física, 3=estrangeiro. */
    private static String documentType(String identificador) {
        if (identificador == null) {
            return "CPF";
        }
        return switch (identificador.trim()) {
            case "1" -> "CNPJ";
            case "3" -> "ESTRANGEIRO";
            default -> "CPF";
        };
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }

    public record ShareholderRow(
            String documentType,
            String name,
            String documentMask,
            String qualificationCode,
            String qualificationDescription,
            LocalDate entryDate,
            String ageRange,
            String companyName,
            String companyStatus,
            String companyCnpj) {
    }

    public record ShareholderCompanyRow(
            String cnpjRaiz,
            String companyName,
            String qualificationCode,
            String qualificationDescription,
            LocalDate entryDate,
            String companyStatus,
            String companyCnpj) {
    }
}
