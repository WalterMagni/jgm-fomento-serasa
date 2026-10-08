package com.portal.serasa.infrastructure.security;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Usuário da requisição, relido do banco.
 *
 * <p>Os controllers antigos têm cada um sua cópia deste método; os da esteira de liberação usam
 * este. Relê do banco porque as marcas de analista e Comitê mudam sem novo login.</p>
 */
@Component
@RequiredArgsConstructor
public class UsuarioLogado {

    private final UserRepository userRepository;

    public UserEntity obter() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            throw new AcessoNegadoException("Não autenticado");
        }
        return userRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new AcessoNegadoException("Usuário não encontrado"));
    }
}
