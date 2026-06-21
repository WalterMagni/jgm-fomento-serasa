package com.portal.serasa.infrastructure.integration.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Cria o cliente BigQuery apenas quando {@code bigquery.enabled=true}.
 * Sem isso, o app sobe normalmente e a consulta de filiais fica indisponível
 * (503), igual ao tratamento do Gemini.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "bigquery.enabled", havingValue = "true")
public class BigQueryConfig {

    @Bean
    public BigQuery bigQuery(
            @Value("${bigquery.project-id:}") String projectId,
            @Value("${bigquery.credentials-path:}") String credentialsPath) throws Exception {
        BigQueryOptions.Builder options = BigQueryOptions.newBuilder();
        if (projectId != null && !projectId.isBlank()) {
            options.setProjectId(projectId);
        }
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            try (InputStream in = new FileInputStream(credentialsPath)) {
                options.setCredentials(GoogleCredentials.fromStream(in));
            }
            log.info("BigQuery habilitado (project={}, credenciais={})", projectId, credentialsPath);
        } else {
            // Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS do ambiente)
            log.info("BigQuery habilitado (project={}, credenciais=ADC)", projectId);
        }
        return options.build().getService();
    }
}
