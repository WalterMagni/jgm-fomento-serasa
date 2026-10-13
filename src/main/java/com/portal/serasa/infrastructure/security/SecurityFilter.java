package com.portal.serasa.infrastructure.security;

import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = recoverToken(request);
        if (token != null) {
            String subject = jwtUtil.validateTokenAndGetSubject(token);
            if (!subject.isEmpty()) {
                Optional<UserEntity> userOpt = userRepository.findByEmail(subject);
                if (userOpt.isPresent() && !emitidoAntesDaTroca(jwtUtil.emitidoEm(token), userOpt.get().getSenhaAlteradaEm())) {
                    UserEntity user = userOpt.get();
                    var authorities = Collections.singletonList(new SimpleGrantedAuthority(user.getRole()));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Login de antes da última troca de senha não vale mais: quem redefiniu a senha derruba as
     * sessões abertas em outros computadores. Token sem data de emissão (antigo) cai junto. A
     * data do token tem precisão de segundos, por isso a troca é comparada truncada.
     */
    static boolean emitidoAntesDaTroca(Instant emitidoEm, Instant senhaAlteradaEm) {
        if (senhaAlteradaEm == null) {
            return false;
        }
        return emitidoEm == null || emitidoEm.isBefore(senhaAlteradaEm.truncatedTo(ChronoUnit.SECONDS));
    }

    private String recoverToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.replace("Bearer ", "");
    }
}
