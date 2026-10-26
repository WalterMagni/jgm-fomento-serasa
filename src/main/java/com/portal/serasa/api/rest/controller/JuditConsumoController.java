package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.judit.JuditConsumoService;
import com.portal.serasa.infrastructure.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/** Aba "Consumo Judit" das Configurações: franquia do mês, consultas do período e liberação do admin. */
@RestController
@RequestMapping("/api/v1/judit")
@RequiredArgsConstructor
public class JuditConsumoController {

    private final JuditConsumoService consumoService;
    private final UsuarioLogado usuarioLogado;

    public record LiberacaoRequest(BigDecimal valor, String motivo) {
    }

    /** @param dias período da tabela de consultas; a franquia é sempre do mês corrente */
    @GetMapping("/consumo")
    public ResponseEntity<JuditConsumoService.Painel> consumo(@RequestParam(defaultValue = "30") int dias) {
        return ResponseEntity.ok(consumoService.painel(dias, usuarioLogado.obter()));
    }

    /** Soma um valor ao limite do mês corrente. Só admin. */
    @PostMapping("/liberacoes")
    public ResponseEntity<JuditConsumoService.Painel> liberar(@RequestBody LiberacaoRequest request) {
        return ResponseEntity.ok(consumoService.liberar(request.valor(), request.motivo(), usuarioLogado.obter()));
    }
}
