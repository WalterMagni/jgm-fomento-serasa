package com.portal.serasa.infrastructure.integration.serasa;

import com.portal.serasa.infrastructure.integration.serasa.dto.SerasaLoginResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import java.util.Base64;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gerencia autenticação na API Serasa Experian.
 * Token dura 60 minutos; renovado automaticamente antes do vencimento.
 */
@Service
@Slf4j
public class SerasaAuthService {

    private static final String LOGIN_URL = "https://api.serasaexperian.com.br/security/iam/v1/client-identities/login";
    private static final long REFRESH_BUFFER_SECONDS = 300; // 5 min antes de expirar

    private final RestClient restClient;
    private final String username;
    private final String password;

    private volatile String cachedToken;
    private volatile long tokenExpiresAt; // epoch seconds
    private final ReentrantLock lock = new ReentrantLock();

    public SerasaAuthService(
            RestClient.Builder restClientBuilder,
            @Value("${serasa.api.username:}") String username,
            @Value("${serasa.api.password:}") String password) {
        this.restClient = restClientBuilder.build();
        this.username = username;
        this.password = password;
    }

    /**
     * Retorna o token Bearer válido, renovando se necessário.
     */
    public String getAccessToken() {
        if (isTokenValid()) {
            return cachedToken;
        }
        lock.lock();
        try {
            if (isTokenValid()) {
                return cachedToken;
            }
            return doLogin();
        } finally {
            lock.unlock();
        }
    }

    private boolean isTokenValid() {
        return cachedToken != null && Instant.now().getEpochSecond() < (tokenExpiresAt - REFRESH_BUFFER_SECONDS);
    }

    private String doLogin() {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("SERASA_API_USER e SERASA_API_PASSWORD devem estar configurados no .env");
        }

        String basicAuth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        log.debug("Autenticando na API Serasa...");

        SerasaLoginResponse response = restClient.post()
                .uri(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Basic " + basicAuth)
                .body("{}")
                .retrieve()
                .body(SerasaLoginResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Falha na autenticação Serasa: resposta inválida");
        }

        cachedToken = response.accessToken();
        // expiresIn pode ser timestamp Unix ou duração em segundos
        long expiresIn = parseExpiresIn(response.expiresIn());
        tokenExpiresAt = expiresIn > 1_000_000_000
                ? expiresIn
                : Instant.now().getEpochSecond() + expiresIn;

        log.info("Token Serasa obtido com sucesso, expira em {}", Instant.ofEpochSecond(tokenExpiresAt));
        return cachedToken;
    }

    private long parseExpiresIn(String expiresIn) {
        if (expiresIn == null || expiresIn.isBlank()) {
            return 3600; // default 1h
        }
        try {
            return Long.parseLong(expiresIn.trim());
        } catch (NumberFormatException e) {
            return 3600;
        }
    }
}
