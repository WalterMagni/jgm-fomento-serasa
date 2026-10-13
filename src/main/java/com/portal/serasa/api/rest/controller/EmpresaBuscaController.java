package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.port.out.CompanyDetailRepository;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.domain.model.CreditAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final CreditAnalysisRepository creditAnalysisRepository;

    /**
     * @param consultaSerasaEm última consulta Serasa da empresa; nula quando nunca foi consultada.
     *                         A abertura de prospecção avisa quando ela passa de 90 dias.
     */
    public record EmpresaEncontrada(String cnpj, String nome, String fantasia, String cidade, String uf,
                                    LocalDateTime consultaSerasaEm) {
    }

    @GetMapping("/busca")
    public ResponseEntity<List<EmpresaEncontrada>> buscar(@RequestParam("q") String termo) {
        if (termo == null || termo.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        var empresas = companyDetailRepository.searchByNameOrDocument(termo, LIMITE);
        // Uma consulta só para a última análise de todas as encontradas.
        Map<String, LocalDateTime> consultas = empresas.isEmpty() ? Map.of()
                : creditAnalysisRepository.findLatestByCnpjIn(empresas.stream().map(e -> e.getDocumentNumber()).toList())
                        .stream()
                        .filter(analise -> analise.getConsultaEm() != null)
                        .collect(Collectors.toMap(CreditAnalysis::getCnpj, CreditAnalysis::getConsultaEm, (a, b) -> a));
        return ResponseEntity.ok(empresas.stream()
                .map(empresa -> new EmpresaEncontrada(empresa.getDocumentNumber(), empresa.getCompanyName(),
                        empresa.getAlias(), empresa.getCity(), empresa.getState(),
                        consultas.get(empresa.getDocumentNumber())))
                .toList());
    }
}
