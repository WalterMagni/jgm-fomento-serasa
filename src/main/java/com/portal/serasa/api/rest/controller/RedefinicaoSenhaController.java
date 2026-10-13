package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.RedefinicaoSenhaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Redefinição de senha pela tela de login, sem estar logado. O código do link vai no corpo, não
 * na URL, para não parar em log de acesso.
 */
@RestController
@RequestMapping("/api/auth/senha")
@RequiredArgsConstructor
public class RedefinicaoSenhaController {

    private final RedefinicaoSenhaService service;

    /** Sempre a mesma resposta, exista o e-mail ou não. */
    @PostMapping("/esqueci")
    public ResponseEntity<Map<String, String>> esqueci(@RequestBody Map<String, String> body) {
        service.solicitar(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Se o e-mail estiver cadastrado, enviamos um link para criar uma senha nova."));
    }

    @PostMapping("/validar")
    public ResponseEntity<Map<String, Boolean>> validar(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(Map.of("valido", service.linkValido(body.get("token"))));
    }

    @PostMapping("/redefinir")
    public ResponseEntity<Map<String, String>> redefinir(@RequestBody Map<String, String> body) {
        try {
            service.redefinir(body.get("token"), body.get("senha"));
            return ResponseEntity.ok(Map.of("message", "Senha redefinida."));
        } catch (IllegalArgumentException | RedefinicaoSenhaService.LinkInvalidoException erro) {
            return ResponseEntity.badRequest().body(Map.of("error", erro.getMessage()));
        }
    }
}
