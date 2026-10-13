package com.portal.serasa.infrastructure.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
public class JwtUtil {

    private final String secret;

    public JwtUtil(
            @Value("${api.security.token.secret:c2VjcmV0LWtleS1xdWUtZGV2ZS10ZXItcGFkcmFvLWJhc2U2NC1hbHRv}") String secret) {
        this.secret = secret;
    }

    public String generateToken(String email, String role) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withIssuer("portal-serasa")
                .withSubject(email)
                .withClaim("role", role)
                // Momento da emissão: login anterior a uma troca de senha é recusado (SecurityFilter).
                .withIssuedAt(Instant.now())
                .withExpiresAt(getExpirationDate())
                .sign(algorithm);
    }

    public String validateTokenAndGetSubject(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("portal-serasa")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return "";
        }
    }

    /**
     * Quando o token foi emitido. Só lê o conteúdo, sem conferir assinatura: chamar depois de
     * {@link #validateTokenAndGetSubject}. Nulo em token antigo, emitido sem a data.
     */
    public Instant emitidoEm(String token) {
        try {
            return JWT.decode(token).getIssuedAtAsInstant();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    private Instant getExpirationDate() {
        return LocalDateTime.now().plusHours(8).toInstant(ZoneOffset.of("-03:00"));
    }
}
