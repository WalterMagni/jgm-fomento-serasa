package com.portal.serasa.infrastructure.integration.cnpj;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dono do pool de conexões para a cópia local do cadastro da Receita Federal
 * (banco carregado pelo <a href="https://github.com/caiopizzol/cnpj-data-pipeline">cnpj-data-pipeline</a>),
 * que roda separado do banco da aplicação.
 *
 * <p>Antes o pool vivia dentro do {@link CompanyBranchClient}. Com mais de um consumidor
 * (filiais e quadro societário) o pool foi extraído para cá, para que os clients compartilhem
 * uma única conexão em vez de abrir um pool por consulta.</p>
 *
 * <p>O pool é criado sob demanda e só quando {@code cnpj.datasource.enabled=true}. Se o banco
 * estiver fora do ar, a aplicação sobe normal e os endpoints que dependem dele respondem 503,
 * mesmo tratamento do Gemini. Em qualquer falha de consulta o consumidor chama {@link #reset()},
 * e o pool é reconstruído na próxima chamada.</p>
 */
@Slf4j
@Component
public class ReceitaDataSourceProvider {

    @Value("${cnpj.datasource.enabled:false}")
    private boolean enabled;

    @Value("${cnpj.datasource.url:}")
    private String url;

    @Value("${cnpj.datasource.username:}")
    private String username;

    @Value("${cnpj.datasource.password:}")
    private String password;

    @Value("${cnpj.datasource.pool-size:5}")
    private int poolSize;

    /**
     * Janela de espera antes de tentar reconectar depois de uma falha. Sem isso, com o banco
     * fora do ar toda chamada a {@link #isAvailable()} paga o {@code connectionTimeout} inteiro,
     * e o custo se multiplica por consumidor e por requisição.
     */
    @Value("${cnpj.datasource.retry-cooldown-seconds:30}")
    private int retryCooldownSeconds;

    private HikariDataSource dataSource;

    private Instant lastFailureAt;

    /** Um JdbcTemplate por teto de linhas — cada consulta tem o seu limite de blindagem. */
    private final Map<Integer, JdbcTemplate> templates = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        initializeIfNeeded();
    }

    @PreDestroy
    void shutdown() {
        reset();
    }

    public synchronized boolean isAvailable() {
        initializeIfNeeded();
        return dataSource != null;
    }

    /**
     * Template pronto para consulta, com {@code setMaxRows(maxRows)} já aplicado.
     *
     * @throws IllegalStateException se a base da Receita estiver indisponível.
     */
    public JdbcTemplate template(int maxRows) {
        if (!isAvailable()) {
            throw new IllegalStateException("Base da Receita indisponivel no momento");
        }
        return templates.computeIfAbsent(maxRows, rows -> {
            JdbcTemplate template = new JdbcTemplate(dataSource);
            template.setMaxRows(rows);
            return template;
        });
    }

    /** Derruba o pool para que a próxima chamada reconecte. Usado após falha de consulta. */
    public synchronized void reset() {
        templates.clear();
        lastFailureAt = Instant.now();
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private synchronized void initializeIfNeeded() {
        if (dataSource != null) {
            return;
        }
        if (isInRetryCooldown()) {
            return;
        }
        if (!enabled || url == null || url.isBlank()) {
            log.info("Base da Receita desabilitada (cnpj.datasource.enabled=false ou url vazia)");
            return;
        }
        HikariDataSource ds = null;
        try {
            ds = new HikariDataSource();
            ds.setJdbcUrl(url);
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setReadOnly(true);
            ds.setMaximumPoolSize(poolSize);
            ds.setPoolName("cnpj-receita-pool");
            ds.setInitializationFailTimeout(-1);
            ds.setConnectionTimeout(3000);

            // Valida a conectividade sem derrubar o boot; se falhar, os endpoints respondem 503.
            ds.getConnection().close();

            this.dataSource = ds;
            this.lastFailureAt = null;
            log.info("Base da Receita habilitada (Postgres CNPJ: {})", url);
        } catch (Exception ex) {
            if (ds != null) {
                ds.close();
            }
            this.dataSource = null;
            this.lastFailureAt = Instant.now();
            log.warn("Base da Receita indisponivel no boot/acesso (Postgres CNPJ: {}): {} — nova tentativa em {}s",
                    url, ex.getMessage(), retryCooldownSeconds);
        }
    }

    private boolean isInRetryCooldown() {
        return lastFailureAt != null
                && Instant.now().isBefore(lastFailureAt.plus(Duration.ofSeconds(retryCooldownSeconds)));
    }
}
