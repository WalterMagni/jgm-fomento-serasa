package com.portal.serasa.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários para JwtUtil.
 * Valida geração, validação e rejeição de tokens JWT.
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-long-enough-for-hmac256-algorithm");
    }

    @Test
    @DisplayName("generateToken: deve gerar token não nulo e não vazio")
    void shouldGenerateNonEmptyToken() {
        String token = jwtUtil.generateToken("user@test.com", "ROLE_USER");

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("validateTokenAndGetSubject: deve retornar o email do subject para token válido")
    void shouldReturnEmailSubject_forValidToken() {
        String email = "admin@portal.com";
        String token = jwtUtil.generateToken(email, "ROLE_ADMIN");

        String subject = jwtUtil.validateTokenAndGetSubject(token);

        assertThat(subject).isEqualTo(email);
    }

    @Test
    @DisplayName("validateTokenAndGetSubject: deve retornar string vazia para token inválido")
    void shouldReturnEmptyString_forInvalidToken() {
        String result = jwtUtil.validateTokenAndGetSubject("token.invalido.assinado-errado");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("validateTokenAndGetSubject: deve retornar string vazia para token nulo ou vazio")
    void shouldReturnEmptyString_forGarbageInput() {
        assertThat(jwtUtil.validateTokenAndGetSubject("")).isEmpty();
        assertThat(jwtUtil.validateTokenAndGetSubject("not-a-jwt")).isEmpty();
    }

    @Test
    @DisplayName("generateToken: tokens gerados para emails distintos devem ser diferentes")
    void shouldGenerateDifferentTokens_forDifferentEmails() {
        String token1 = jwtUtil.generateToken("user1@test.com", "ROLE_USER");
        String token2 = jwtUtil.generateToken("user2@test.com", "ROLE_USER");

        assertThat(token1).isNotEqualTo(token2);
    }

    @Test
    @DisplayName("validateTokenAndGetSubject: token assinado com secret diferente deve ser rejeitado")
    void shouldRejectToken_signedWithDifferentSecret() {
        JwtUtil outroJwt = new JwtUtil("outro-secret-completamente-diferente-123456");
        String tokenDeOutro = outroJwt.generateToken("user@test.com", "ROLE_USER");

        String result = jwtUtil.validateTokenAndGetSubject(tokenDeOutro);

        assertThat(result).isEmpty();
    }
}
