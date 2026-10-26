package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.empresa.EmpresaLimiteSacadoService;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Limite da empresa como sacado, na Visão 360º; a liberação mostra o atual em cada sacado. O
 * caminho {@code /{cnpj}/limite-sacado} não colide com {@code /{cnpj}} do CompanyDetailController.
 */
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class EmpresaLimiteSacadoController {

    private final EmpresaLimiteSacadoService limiteService;
    private final UserRepository userRepository;

    /** {@code valor} nulo retira o limite. */
    public record LimiteRequest(BigDecimal valor) {
    }

    @GetMapping("/{cnpj}/limite-sacado")
    public ResponseEntity<EmpresaLimiteSacadoService.LimiteAtual> consultar(@PathVariable String cnpj) {
        return ResponseEntity.ok(limiteService.consultar(cnpj));
    }

    @PutMapping("/{cnpj}/limite-sacado")
    public ResponseEntity<EmpresaLimiteSacadoService.LimiteAtual> definir(@PathVariable String cnpj,
                                                                          @RequestBody LimiteRequest request) {
        return ResponseEntity.ok(limiteService.definir(cnpj, request == null ? null : request.valor(), usuarioAutenticado()));
    }

    private UserEntity usuarioAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            throw new AcessoNegadoException("Não autenticado");
        }
        return userRepository.findByEmail(user.getEmail())
                .orElseThrow(() -> new AcessoNegadoException("Usuário não encontrado"));
    }
}
