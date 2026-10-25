package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.arquivo.TicketArquivo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * Abre na aba um arquivo protegido, pelo ticket que {@link TicketArquivo} emitiu para quem estava
 * logado. Única rota de arquivo liberada no SecurityConfig: quem confere o acesso é o ticket.
 */
@RestController
@RequestMapping("/api/v1/arquivos")
@RequiredArgsConstructor
public class ArquivoController {

    private final TicketArquivo ticketArquivo;

    @GetMapping("/{ticket}")
    public ResponseEntity<byte[]> abrir(@PathVariable String ticket) {
        return ticketArquivo.abrir(ticket)
                .map(arquivo -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(arquivo.contentType()))
                        // inline: PDF e imagem o navegador mostra na própria aba, em vez de baixar.
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename(arquivo.nome() == null ? "arquivo" : arquivo.nome(), StandardCharsets.UTF_8)
                                .build().toString())
                        .header("X-Content-Type-Options", "nosniff")
                        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                        .body(arquivo.dados()))
                .orElseGet(() -> ResponseEntity.status(404).build());
    }
}
