package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Busca rápida de empresa cadastrada por nome ou CNPJ, para os seletores e as menções.
 *
 * <p>A busca de parceiras faz o mesmo, mas presa a um CNPJ base. Esta é a versão solta. O caminho
 * literal {@code /busca} tem precedência sobre {@code /{cnpj}} no Spring, então não colide com a
 * página da empresa.</p>
 */
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
public class EmpresaBuscaController {

    private static final int LIMITE = 8;

    private final CompanyDetailRepository companyDetailRepository;

    public record EmpresaEncontrada(String cnpj, String nome, String fantasia, String cidade, String uf) {
    }

    @GetMapping("/busca")
    public ResponseEntity<List<EmpresaEncontrada>> buscar(@RequestParam("q") String termo) {
        if (termo == null || termo.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(companyDetailRepository.searchByNameOrDocument(termo, LIMITE).stream()
                .map(empresa -> new EmpresaEncontrada(empresa.getDocumentNumber(), empresa.getCompanyName(),
                        empresa.getAlias(), empresa.getCity(), empresa.getState()))
                .toList());
    }
}
