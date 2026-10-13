package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.empresa.EmpresaNotaService;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.model.NotaEmpresa;
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

/**
 * Nota da equipe para a empresa (A a D), mostrada ao lado do nome na Visão 360º e filtrável na
 * carteira. O caminho {@code /{cnpj}/nota} não colide com {@code /{cnpj}} do CompanyDetailController.
 */
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class EmpresaNotaController {

    private final EmpresaNotaService notaService;
    private final UserRepository userRepository;

    /** {@code nota} nula ou em branco retira a nota. */
    public record NotaRequest(String nota) {
    }

    @GetMapping("/{cnpj}/nota")
    public ResponseEntity<EmpresaNotaService.NotaAtual> consultar(@PathVariable String cnpj) {
        return ResponseEntity.ok(notaService.consultar(cnpj));
    }

    @PutMapping("/{cnpj}/nota")
    public ResponseEntity<EmpresaNotaService.NotaAtual> atribuir(@PathVariable String cnpj,
                                                                 @RequestBody NotaRequest request) {
        return ResponseEntity.ok(notaService.atribuir(cnpj, lerNota(request == null ? null : request.nota()),
                usuarioAutenticado()));
    }

    private static NotaEmpresa lerNota(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return NotaEmpresa.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Nota inválida: use A, B, C ou D.");
        }
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
