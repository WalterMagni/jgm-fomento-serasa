package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.ClientCreateRequest;
import com.portal.serasa.api.rest.dto.request.ClientUpdateRequest;
import com.portal.serasa.api.rest.dto.response.ClientResponse;
import com.portal.serasa.application.service.ClientService;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Validated
public class ClientController {

    private final ClientService clientService;

    @PostMapping
    public ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        Client client = Client.builder()
                .documentNumber(request.getDocumentNumber().replaceAll("\\D", ""))
                .name(request.getName())
                .email(request.getEmail())
                .phones(request.getPhones())
                .build();
        Client saved = clientService.create(client);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientResponse> findById(@PathVariable UUID id) {
        Client client = clientService.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado: " + id));
        return ResponseEntity.ok(toResponse(client));
    }

    @GetMapping("/document/{documentNumber}")
    public ResponseEntity<ClientResponse> findByDocumentNumber(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String documentNumber) {
        Client client = clientService.findByDocumentNumber(documentNumber)
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado para documento: " + documentNumber));
        return ResponseEntity.ok(toResponse(client));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClientResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ClientUpdateRequest request) {
        Client updates = Client.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phones(request.getPhones())
                .build();
        Client saved = clientService.update(id, updates);
        return ResponseEntity.ok(toResponse(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable UUID id) {
        clientService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/document/{documentNumber}")
    public ResponseEntity<Void> deleteByDocumentNumber(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String documentNumber) {
        clientService.deleteByDocumentNumber(documentNumber);
        return ResponseEntity.noContent().build();
    }

    private ClientResponse toResponse(Client c) {
        return ClientResponse.builder()
                .id(c.getId())
                .documentNumber(c.getDocumentNumber())
                .name(c.getName())
                .email(c.getEmail())
                .phones(c.getPhones())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
