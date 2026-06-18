package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.CompanyNoteRequest;
import com.portal.serasa.api.rest.dto.response.CompanyNoteAttachmentResponse;
import com.portal.serasa.api.rest.dto.response.CompanyNoteResponse;
import com.portal.serasa.api.rest.dto.response.PersonAnalysisResponse;
import com.portal.serasa.api.rest.mapper.PersonAnalysisDtoMapper;
import com.portal.serasa.application.service.PersonNoteService;
import com.portal.serasa.application.service.PersonProfileService;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/person")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PersonController {

    private final PersonProfileService personProfileService;
    private final PersonNoteService personNoteService;
    private final PersonAnalysisDtoMapper dtoMapper;
    private final UserRepository userRepository;

    /** Consulta a API Serasa PF para o CPF e persiste o resultado. */
    @PostMapping("/enrich/serasa/{cpf}")
    public ResponseEntity<PersonAnalysisResponse> enrichBySerasa(
            @PathVariable @NotBlank @Size(min = 11, max = 14)
            @Pattern(regexp = "^[0-9.\\-]+$", message = "CPF deve conter apenas dígitos ou formatação") String cpf) {
        PersonAnalysis analysis = personProfileService.enrichBySerasa(cpf.replaceAll("\\D", ""));
        return ResponseEntity.ok(toResponse(analysis));
    }

    /**
     * [DEV] Processa um JSON bruto da Serasa PF sem chamar a API real.
     * Envie o conteúdo do PF.json no body (Content-Type: application/json).
     */
    @PostMapping("/enrich/serasa/{cpf}/from-mock")
    public ResponseEntity<PersonAnalysisResponse> enrichBySerasaFromMock(
            @PathVariable @NotBlank @Size(min = 11, max = 14)
            @Pattern(regexp = "^[0-9.\\-]+$", message = "CPF deve conter apenas dígitos ou formatação") String cpf,
            @RequestBody String rawJson) {
        PersonAnalysis analysis = personProfileService.enrichBySerasaFromJson(cpf.replaceAll("\\D", ""), rawJson);
        return ResponseEntity.ok(toResponse(analysis));
    }

    /** Retorna a análise mais recente para o CPF. */
    @GetMapping("/{cpf}/profile")
    public ResponseEntity<PersonAnalysisResponse> getProfile(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf) {
        PersonAnalysis analysis = personProfileService.getProfileByCpf(cpf.replaceAll("\\D", ""));
        return ResponseEntity.ok(toResponse(analysis));
    }

    @GetMapping("/{cpf}/notes")
    public ResponseEntity<?> listNotes(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cpf.replaceAll("\\D", "");
        return ResponseEntity.ok(
                personNoteService.listByCpf(normalized, currentUser).stream()
                        .map(this::toPersonNoteResponse)
                        .toList()
        );
    }

    @PostMapping(value = "/{cpf}/notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createNote(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf,
            @RequestPart("content") String content,
            @RequestPart(value = "parentNoteId", required = false) String parentNoteId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) java.util.List<MultipartFile> files) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cpf.replaceAll("\\D", "");
        CompanyNoteRequest request = CompanyNoteRequest.builder().content(content).build();
        UUID parsedParentNoteId = null;
        if (parentNoteId != null && !parentNoteId.isBlank()) {
            try {
                parsedParentNoteId = UUID.fromString(parentNoteId.trim());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(java.util.Map.of("error", "ID da anotação citada é inválido"));
            }
        }
        java.util.List<MultipartFile> allFiles = new java.util.ArrayList<>();
        if (file != null && !file.isEmpty()) {
            allFiles.add(file);
        }
        if (files != null) {
            allFiles.addAll(files.stream().filter(f -> f != null && !f.isEmpty()).toList());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(
                toPersonNoteResponse(personNoteService.create(normalized, request.getContent(), parsedParentNoteId, allFiles, currentUser))
        );
    }

    @DeleteMapping("/{cpf}/notes/{noteId}")
    public ResponseEntity<?> deleteNote(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf,
            @PathVariable UUID noteId) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cpf.replaceAll("\\D", "");
        personNoteService.delete(normalized, noteId, currentUser);
        return ResponseEntity.ok(java.util.Map.of("message", "Anotação removida com sucesso"));
    }

    @GetMapping("/{cpf}/notes/{noteId}/attachment")
    public ResponseEntity<?> downloadNoteAttachment(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf,
            @PathVariable UUID noteId) {
        return downloadNoteAttachmentById(cpf, noteId, null);
    }

    @GetMapping("/{cpf}/notes/{noteId}/attachments/{attachmentId}")
    public ResponseEntity<?> downloadNoteAttachmentById(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf,
            @PathVariable UUID noteId,
            @PathVariable(required = false) UUID attachmentId) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cpf.replaceAll("\\D", "");
        PersonNoteService.PersonNoteAttachmentView attachment = personNoteService.getAttachment(normalized, noteId, attachmentId);
        String fileName = attachment.getFileName() != null ? attachment.getFileName() : "anotacao-anexo";
        String contentType = attachment.getContentType() != null && !attachment.getContentType().isBlank()
                ? attachment.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "attachment; filename=\"" + fileName.replace("\"", "") + "\"")
                .body(attachment.getContent());
    }

    /** Lista paginada de análises PF. Parâmetro search filtra por CPF ou nome. */
    @GetMapping("/profiles")
    public ResponseEntity<Page<PersonAnalysisResponse>> findAll(
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<PersonAnalysis> page = personProfileService.findAllProfiles(pageable, search);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    /** Remove todas as análises do CPF. */
    @DeleteMapping("/{cpf}")
    public ResponseEntity<Void> delete(
            @PathVariable @NotBlank @Size(min = 11, max = 14) String cpf) {
        personProfileService.deleteProfile(cpf.replaceAll("\\D", ""));
        return ResponseEntity.noContent().build();
    }

    private PersonAnalysisResponse toResponse(PersonAnalysis analysis) {
        PersonAnalysisResponse response = dtoMapper.toResponse(analysis);
        response.setRegisteredCompanyCnpjs(personProfileService.findRegisteredCompanyCnpjs(analysis));
        return response;
    }

    private CompanyNoteResponse toPersonNoteResponse(PersonNoteService.PersonNoteView note) {
        return CompanyNoteResponse.builder()
                .id(note.getId())
                .content(note.getContent())
                .authorName(note.getAuthorName())
                .authorEmail(note.getAuthorEmail())
                .createdAt(note.getCreatedAt())
                .canDelete(note.isCanDelete())
                .repliedToId(note.getRepliedToId())
                .repliedToAuthorName(note.getRepliedToAuthorName())
                .repliedToContent(note.getRepliedToContent())
                .hasAttachment(note.isHasAttachment())
                .attachmentFileName(note.getAttachmentFileName())
                .attachmentContentType(note.getAttachmentContentType())
                .attachmentSize(note.getAttachmentSize())
                .attachments(note.getAttachments() != null ? note.getAttachments().stream()
                        .map(attachment -> CompanyNoteAttachmentResponse.builder()
                                .id(attachment.getId())
                                .fileName(attachment.getFileName())
                                .contentType(attachment.getContentType())
                                .fileSize(attachment.getFileSize())
                                .build())
                        .toList() : java.util.List.of())
                .build();
    }

    private UserEntity getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            return null;
        }
        return userRepository.findByEmail(user.getEmail()).orElse(null);
    }
}
