package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.CompanyDetailCreateRequest;
import com.portal.serasa.api.rest.dto.request.CompanyCommercialInformationRequest;
import com.portal.serasa.api.rest.dto.request.CompanyDocumentFileOperationRequest;
import com.portal.serasa.api.rest.dto.request.CompanyDocumentRootRequest;
import com.portal.serasa.api.rest.dto.request.CompanyNoteRequest;
import com.portal.serasa.api.rest.dto.request.CompanyDetailUpdateRequest;
import com.portal.serasa.api.rest.dto.response.AiAnalysisResponse;
import com.portal.serasa.api.rest.dto.response.ClientProfileResponse;
import com.portal.serasa.api.rest.dto.response.CompanyCommercialInformationResponse;
import com.portal.serasa.api.rest.dto.response.CompanyDocumentsResponse;
import com.portal.serasa.api.rest.dto.response.CompanyNoteAttachmentResponse;
import com.portal.serasa.api.rest.dto.response.CompanyNoteResponse;
import com.portal.serasa.api.rest.dto.response.CreditAnalysisHistoryItemResponse;
import com.portal.serasa.api.rest.dto.response.ClientResponse;
import com.portal.serasa.api.rest.dto.response.CompanyDetailResponse;
import com.portal.serasa.api.rest.dto.response.CreditAnalysisResponse;
import com.portal.serasa.api.rest.dto.response.PersonAnalysisSummaryResponse;
import com.portal.serasa.api.rest.mapper.CreditAnalysisDtoMapper;
import com.portal.serasa.api.rest.mapper.PersonAnalysisDtoMapper;
import com.portal.serasa.domain.model.PersonAnalysis;
import com.portal.serasa.application.port.out.CreditAnalysisRepository;
import com.portal.serasa.application.service.ClientProfileService;
import com.portal.serasa.application.service.CompanyCommercialInformationService;
import com.portal.serasa.application.service.CompanyCommercialInformationService.CompanyCommercialInformationView;
import com.portal.serasa.application.service.CompanyDocumentService;
import com.portal.serasa.application.service.CompanyNoteService;
import com.portal.serasa.application.service.ClientProfileService.ResyncTarget;
import com.portal.serasa.application.service.CompanyDetailService;
import com.portal.serasa.domain.model.Client;
import com.portal.serasa.domain.model.CompanyDetail;
import com.portal.serasa.domain.model.CreditAnalysis;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ClientEntity;
import com.portal.serasa.infrastructure.persistence.entity.CompanyCommercialInformationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.serasa.infrastructure.email.EmailService;
import com.portal.serasa.infrastructure.email.PdfReportService;
import com.portal.serasa.infrastructure.integration.gemini.GeminiAiService;
import com.portal.serasa.infrastructure.integration.gemini.GeminiAnalysisResult;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@Validated
@Slf4j
public class CompanyDetailController {

    private final CompanyDetailService companyDetailService;
    private final CompanyCommercialInformationService companyCommercialInformationService;
    private final CompanyDocumentService companyDocumentService;
    private final CompanyNoteService companyNoteService;
    private final ClientProfileService clientProfileService;
    private final CreditAnalysisDtoMapper creditAnalysisDtoMapper;
    private final PersonAnalysisDtoMapper personAnalysisDtoMapper;
    private final CreditAnalysisRepository creditAnalysisRepository;
    private final GeminiAiService geminiAiService;
    private final EmailService emailService;
    private final PdfReportService pdfReportService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    /** Enriquece dados da empresa consultando a API CNPJ Já. */
    @PostMapping("/enrich/cnpja/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> enrichByCnpja(
            @PathVariable @NotBlank @Size(min = 14, max = 18) @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação") String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDetail detail = clientProfileService.enrichByCnpja(normalized);
        return ResponseEntity.ok(toResponse(detail));
    }

    /**
     * Enriquece dados da empresa consultando a API Serasa (Credit Rating). Cliente
     * deve existir.
     */
    @PostMapping("/enrich/serasa/{cnpj}")
    public ResponseEntity<CreditAnalysisResponse> enrichBySerasa(
            @PathVariable @NotBlank @Size(min = 14, max = 18) @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação") String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CreditAnalysis analysis = clientProfileService.enrichBySerasa(normalized);
        return ResponseEntity.ok(creditAnalysisDtoMapper.toResponse(analysis));
    }

    /**
     * [DEV] Processa um JSON bruto da Serasa sem chamar a API real.
     * Envie o conteúdo do response.json no body da requisição (Content-Type: application/json).
     */
    @PostMapping("/enrich/serasa/{cnpj}/from-mock")
    public ResponseEntity<CreditAnalysisResponse> enrichBySerasaFromMock(
            @PathVariable @NotBlank @Size(min = 14, max = 18) @Pattern(regexp = "^[0-9.\\-/]+$", message = "CNPJ deve conter apenas dígitos ou formatação") String cnpj,
            @RequestBody String rawJson) {
        String normalized = cnpj.replaceAll("\\D", "");
        CreditAnalysis analysis = clientProfileService.enrichBySerasaFromJson(normalized, rawJson);
        return ResponseEntity.ok(creditAnalysisDtoMapper.toResponse(analysis));
    }

    /** Retorna análise de IA cached (sem chamar Gemini). */
    @GetMapping("/{cnpj}/ai-analysis")
    public ResponseEntity<AiAnalysisResponse> getAiAnalysis(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CreditAnalysis ca = creditAnalysisRepository.findLatestByCnpj(normalized)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nenhum relatório Serasa encontrado para CNPJ: " + normalized));

        if (ca.getAiAnalysis() == null || ca.getAiAnalysis().isBlank()) {
            return ResponseEntity.ok(AiAnalysisResponse.builder()
                    .available(false)
                    .errorMessage("Análise não gerada. Clique em Gerar Análise.")
                    .build());
        }

        try {
            GeminiAnalysisResult cached = objectMapper.readValue(ca.getAiAnalysis(), GeminiAnalysisResult.class);
            return ResponseEntity.ok(toAiResponse(cached, ca.getAiAnalysisDate()));
        } catch (Exception e) {
            return ResponseEntity.ok(AiAnalysisResponse.builder()
                    .available(false)
                    .errorMessage("Erro ao ler análise em cache.")
                    .build());
        }
    }

    /** Força nova análise Gemini e persiste no banco. */
    @PostMapping("/{cnpj}/ai-analysis/refresh")
    public ResponseEntity<AiAnalysisResponse> refreshAiAnalysis(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CreditAnalysis ca = creditAnalysisRepository.findLatestByCnpj(normalized)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nenhum relatório Serasa encontrado para CNPJ: " + normalized));

        GeminiAnalysisResult result = geminiAiService.analyze(ca);
        if (result.isAvailable()) {
            try {
                String json = objectMapper.writeValueAsString(result);
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                creditAnalysisRepository.saveAiAnalysis(ca.getId(), json, now);
            } catch (Exception e) {
                // log only — don't fail the response
            }
        }
        return ResponseEntity.ok(toAiResponse(result, result.isAvailable() ? java.time.LocalDateTime.now() : null));
    }

    /** Perfil completo: dados CNPJ Já + Serasa. */
    @GetMapping("/{cnpj}/profile")
    public ResponseEntity<ClientProfileResponse> getProfile(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(required = false) Long analysisId) {
        String normalized = cnpj.replaceAll("\\D", "");
        ClientProfileService.ClientProfile profile = clientProfileService.getProfileByCnpj(normalized, analysisId);
        return ResponseEntity.ok(toProfileResponse(profile));
    }

    @GetMapping("/{cnpj}/notes")
    public ResponseEntity<?> listNotes(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(
                companyNoteService.listByCnpj(normalized, currentUser).stream()
                        .map(this::toCompanyNoteResponse)
                        .toList()
        );
    }

    @PostMapping(value = "/{cnpj}/notes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createNote(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestPart("content") String content,
            @RequestPart(value = "parentNoteId", required = false) String parentNoteId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestPart(value = "files", required = false) java.util.List<MultipartFile> files) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
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
                toCompanyNoteResponse(companyNoteService.create(normalized, request.getContent(), parsedParentNoteId, allFiles, currentUser))
        );
    }

    @DeleteMapping("/{cnpj}/notes/{noteId}")
    public ResponseEntity<?> deleteNote(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable java.util.UUID noteId) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        companyNoteService.delete(normalized, noteId, currentUser);
        return ResponseEntity.ok(java.util.Map.of("message", "Anotação removida com sucesso"));
    }

    @PatchMapping("/{cnpj}/notes/{noteId}")
    public ResponseEntity<?> updateNote(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable java.util.UUID noteId,
            @Valid @RequestBody CompanyNoteRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(toCompanyNoteResponse(
                companyNoteService.update(normalized, noteId, request.getContent(), currentUser)
        ));
    }

    @PostMapping(value = "/{cnpj}/notes/{noteId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addNoteAttachments(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable java.util.UUID noteId,
            @RequestPart(value = "files", required = false) java.util.List<MultipartFile> files) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(toCompanyNoteResponse(
                companyNoteService.addAttachments(normalized, noteId, files, currentUser)
        ));
    }

    @DeleteMapping("/{cnpj}/notes/{noteId}/attachment")
    public ResponseEntity<?> deleteLegacyNoteAttachment(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable java.util.UUID noteId) {
        return deleteNoteAttachment(cnpj, noteId, null);
    }

    @DeleteMapping("/{cnpj}/notes/{noteId}/attachments/{attachmentId}")
    public ResponseEntity<?> deleteNoteAttachment(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable java.util.UUID noteId,
            @PathVariable(required = false) java.util.UUID attachmentId) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        companyNoteService.deleteAttachment(normalized, noteId, attachmentId, currentUser);
        return ResponseEntity.ok(java.util.Map.of("message", "Anexo removido com sucesso"));
    }

    @GetMapping("/commercial-information")
    public ResponseEntity<?> listAllCommercialInformation() {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        return ResponseEntity.ok(
                companyCommercialInformationService.listAll().stream()
                        .map(view -> toCommercialInformationResponse(view, currentUser))
                        .toList()
        );
    }

    @GetMapping("/{cnpj}/commercial-information")
    public ResponseEntity<?> listCommercialInformation(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(
                companyCommercialInformationService.listByCnpj(normalized).stream()
                        .map(record -> toCommercialInformationResponse(record, currentUser))
                        .toList()
        );
    }

    @PostMapping("/{cnpj}/commercial-information")
    public ResponseEntity<?> createCommercialInformation(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyCommercialInformationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.status(HttpStatus.CREATED).body(
                toCommercialInformationResponse(companyCommercialInformationService.create(normalized, request, currentUser), currentUser)
        );
    }

    @PutMapping("/{cnpj}/commercial-information/{recordId}")
    public ResponseEntity<?> updateCommercialInformation(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable UUID recordId,
            @Valid @RequestBody CompanyCommercialInformationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(
                toCommercialInformationResponse(companyCommercialInformationService.update(normalized, recordId, request, currentUser), currentUser)
        );
    }

    @DeleteMapping("/{cnpj}/commercial-information/{recordId}")
    public ResponseEntity<?> deleteCommercialInformation(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable UUID recordId) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        companyCommercialInformationService.delete(normalized, recordId, currentUser);
        return ResponseEntity.ok(java.util.Map.of("message", "Informação comercial removida com sucesso"));
    }

    @GetMapping("/{cnpj}/documents")
    public ResponseEntity<?> listCompanyDocuments(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(required = false) String path) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(companyDocumentService.list(normalized, path));
    }

    @PostMapping("/{cnpj}/documents/root")
    public ResponseEntity<?> mapCompanyDocumentRoot(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentRootRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDocumentsResponse response = companyDocumentService.mapRoot(normalized, request.getRootPath(), currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{cnpj}/documents/default-folder")
    public ResponseEntity<?> createDefaultCompanyDocumentFolder(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        return ResponseEntity.ok(companyDocumentService.createDefaultFolder(normalized, currentUser));
    }

    @DeleteMapping("/{cnpj}/documents/root")
    public ResponseEntity<?> removeCompanyDocumentRoot(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.removeMapping(normalized);
        return ResponseEntity.ok(java.util.Map.of("message", "Mapeamento de pasta removido"));
    }

    @GetMapping("/{cnpj}/documents/folder-picker")
    public ResponseEntity<?> browseCompanyDocumentBaseFolders(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(required = false) String path) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        return ResponseEntity.ok(companyDocumentService.listBaseDirectories(path));
    }

    @GetMapping("/{cnpj}/documents/file")
    public ResponseEntity<?> openCompanyDocumentFile(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam String path,
            @RequestParam(defaultValue = "false") boolean download) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDocumentService.DocumentFile file = companyDocumentService.getFile(normalized, path);
        try {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(file.getPath()));
            String disposition = download ? "attachment" : "inline";
            String fileName = file.getFileName().replace("\"", "");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(file.getContentType()))
                    .contentLength(Files.size(file.getPath()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + fileName + "\"")
                    .body(resource);
        } catch (java.io.IOException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Não foi possível abrir o arquivo"));
        }
    }

    @PostMapping("/{cnpj}/documents/file/open")
    public ResponseEntity<?> openCompanyDocumentFileWithDefaultApplication(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam String path) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.openFileWithDefaultApplication(normalized, path);
        return ResponseEntity.ok(java.util.Map.of("message", "Arquivo aberto no aplicativo padrão"));
    }

    @PatchMapping("/{cnpj}/documents/file/rename")
    public ResponseEntity<?> renameCompanyDocumentFile(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentFileOperationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.renameFile(normalized, request.getPath(), request.getNewName());
        return ResponseEntity.ok(java.util.Map.of("message", "Arquivo renomeado"));
    }

    @PostMapping("/{cnpj}/documents/file/duplicate")
    public ResponseEntity<?> duplicateCompanyDocumentFile(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentFileOperationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.duplicateFile(normalized, request.getPath());
        return ResponseEntity.ok(java.util.Map.of("message", "Arquivo duplicado"));
    }

    @PostMapping("/{cnpj}/documents/item/explorer")
    public ResponseEntity<?> openCompanyDocumentItemInExplorer(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentFileOperationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.openItemInFileExplorer(normalized, request.getPath());
        return ResponseEntity.ok(java.util.Map.of("message", "Item aberto no explorador de arquivos"));
    }

    @PatchMapping("/{cnpj}/documents/item/rename")
    public ResponseEntity<?> renameCompanyDocumentItem(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentFileOperationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.renameItem(normalized, request.getPath(), request.getNewName());
        return ResponseEntity.ok(java.util.Map.of("message", "Item renomeado"));
    }

    @PostMapping("/{cnpj}/documents/item/duplicate")
    public ResponseEntity<?> duplicateCompanyDocumentItem(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentFileOperationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.duplicateItem(normalized, request.getPath());
        return ResponseEntity.ok(java.util.Map.of("message", "Item duplicado"));
    }

    @PostMapping("/{cnpj}/documents/folder")
    public ResponseEntity<?> createCompanyDocumentFolder(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDocumentFileOperationRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.createFolder(normalized, request.getPath(), request.getNewName());
        return ResponseEntity.ok(java.util.Map.of("message", "Pasta criada"));
    }

    @PostMapping(value = "/{cnpj}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCompanyDocumentFiles(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(required = false) String path,
            @RequestPart("files") java.util.List<MultipartFile> files) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }

        String normalized = cnpj.replaceAll("\\D", "");
        companyDocumentService.uploadFiles(normalized, path, files);
        return ResponseEntity.ok(java.util.Map.of("message", "Arquivo(s) enviado(s)"));
    }

    @GetMapping("/{cnpj}/notes/{noteId}/attachment")
    public ResponseEntity<?> downloadNoteAttachment(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable UUID noteId) {
        return downloadNoteAttachmentById(cnpj, noteId, null);
    }

    @GetMapping("/{cnpj}/notes/{noteId}/attachments/{attachmentId}")
    public ResponseEntity<?> downloadNoteAttachmentById(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @PathVariable UUID noteId,
            @PathVariable(required = false) UUID attachmentId) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(java.util.Map.of("error", "Não autenticado"));
        }
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyNoteService.CompanyNoteAttachmentView attachment = companyNoteService.getAttachment(normalized, noteId, attachmentId);
        String fileName = attachment.getFileName() != null ? attachment.getFileName() : "anotacao-anexo";
        String contentType = attachment.getContentType() != null && !attachment.getContentType().isBlank()
                ? attachment.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header("Content-Disposition", "attachment; filename=\"" + fileName.replace("\"", "") + "\"")
                .body(attachment.getContent());
    }

    @GetMapping
    public ResponseEntity<Page<CompanyDetailResponse>> findAll(Pageable pageable) {
        return ResponseEntity.ok(companyDetailService.findAll(pageable).map(this::toResponse));
    }

    @GetMapping("/profiles")
    public ResponseEntity<Page<ClientProfileResponse>> findProfiles(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String visaoCedente,
            @RequestParam(required = false) String analysisStatus,
            Pageable pageable) {
        return ResponseEntity.ok(
                clientProfileService.findAllProfiles(pageable, search, visaoCedente, analysisStatus)
                        .map(this::toProfileResponse));
    }

    @GetMapping("/profiles/metrics")
    public ResponseEntity<ClientProfileService.PortfolioMetrics> getPortfolioMetrics() {
        return ResponseEntity.ok(clientProfileService.getPortfolioMetrics());
    }

    @GetMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> findByCnpj(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        String normalized = cnpj.replaceAll("\\D", "");
        CompanyDetail detail = companyDetailService.findByDocumentNumber(normalized)
                .orElseThrow(() -> new EntityNotFoundException("Empresa não encontrada para CNPJ: " + normalized));
        return ResponseEntity.ok(toResponse(detail));
    }

    @PostMapping
    public ResponseEntity<CompanyDetailResponse> create(@Valid @RequestBody CompanyDetailCreateRequest request) {
        CompanyDetail detail = CompanyDetail.builder()
                .documentNumber(request.getDocumentNumber().replaceAll("\\D", ""))
                .companyName(request.getCompanyName())
                .build();
        CompanyDetail saved = companyDetailService.create(detail);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    /** Correção manual parcial dos dados cadastrais (apenas campos enviados). */
    @PatchMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> patch(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDetailUpdateRequest request) {
        CompanyDetail updates = mapUpdateRequestToDomain(request);
        CompanyDetail saved = companyDetailService.update(cnpj.replaceAll("\\D", ""), updates);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PutMapping("/{cnpj}")
    public ResponseEntity<CompanyDetailResponse> update(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @Valid @RequestBody CompanyDetailUpdateRequest request) {
        CompanyDetail updates = mapUpdateRequestToDomain(request);
        CompanyDetail saved = companyDetailService.update(cnpj.replaceAll("\\D", ""), updates);
        return ResponseEntity.ok(toResponse(saved));
    }

    /**
     * Re-sincronização: consulta APIs externas e atualiza dados. target: CNPJA,
     * SERASA ou ALL.
     */
    @PostMapping("/{cnpj}/resync")
    public ResponseEntity<ClientProfileResponse> resync(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(defaultValue = "ALL") String target) {
        String normalized = cnpj.replaceAll("\\D", "");
        ResyncTarget resyncTarget = parseResyncTarget(target);
        ClientProfileService.ClientProfile profile = clientProfileService.resync(normalized, resyncTarget);
        return ResponseEntity.ok(toProfileResponse(profile));
    }

    private AiAnalysisResponse toAiResponse(GeminiAnalysisResult r, java.time.LocalDateTime date) {
        return AiAnalysisResponse.builder()
                .available(r.isAvailable())
                .parecer(r.getParecer())
                .visaoCedente(r.getVisaoCedente())
                .nivelRisco(r.getNivelRisco())
                .recomendacao(r.getRecomendacao())
                .pontosFortes(r.getPontosFortes())
                .pontosAtencao(r.getPontosAtencao())
                .errorMessage(r.getErrorMessage())
                .analysisDate(date)
                .build();
    }

    private ResyncTarget parseResyncTarget(String target) {
        return switch (target.toUpperCase()) {
            case "CNPJA" -> ResyncTarget.CNPJA;
            case "SERASA" -> ResyncTarget.SERASA;
            case "ALL" -> ResyncTarget.ALL;
            default -> throw new IllegalArgumentException("target inválido. Use: CNPJA, SERASA ou ALL");
        };
    }

    @DeleteMapping("/{cnpj}")
    public ResponseEntity<Void> delete(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        clientProfileService.deleteProfile(cnpj);
        return ResponseEntity.noContent().build();
    }

    /** Gera o PDF oficial de análise usado pelo portal e pelo envio de e-mail. */
    @GetMapping(value = "/{cnpj}/report-pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> gerarPdfAnalise(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj) {
        CreditAnalysis ca = carregarAnaliseComAi(cnpj.replaceAll("\\D", ""));
        byte[] pdfBytes = pdfReportService.generate(ca, parseAiAnalysis(ca));
        String fileName = buildPdfFileName(ca);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"" + fileName + "\"")
                .body(pdfBytes);
    }

    /** Dispara manualmente o e-mail de análise para a equipe comercial (requer visaoCedente = SIM). */
    @PostMapping(value = "/{cnpj}/email-cedente", consumes = {"multipart/form-data", "application/x-www-form-urlencoded", "application/json", "*/*"})
    public ResponseEntity<?> enviarEmailCedente(
            @PathVariable @NotBlank @Size(min = 14, max = 18) String cnpj,
            @RequestParam(value = "pdf", required = false) org.springframework.web.multipart.MultipartFile pdf) {
        String normalized = cnpj.replaceAll("\\D", "");
        CreditAnalysis ca = carregarAnaliseComAi(normalized);
        if (!"SIM".equals(ca.getVisaoCedente())) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("error", "Empresa não possui Visão Cedente = SIM"));
        }
        try {
            emailService.notificarCedente(ca, pdf);
            return ResponseEntity.ok(java.util.Map.of("message", "E-mail de análise enviado com sucesso"));
        } catch (IllegalStateException e) {
            log.error("Falha ao enviar e-mail manual de análise para CNPJ={}", normalized, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    private CreditAnalysis carregarAnaliseComAi(String normalizedCnpj) {
        CreditAnalysis ca = creditAnalysisRepository.findLatestByCnpj(normalizedCnpj)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Nenhum relatório Serasa encontrado para CNPJ: " + normalizedCnpj));

        if (ca.getAiAnalysis() == null || ca.getAiAnalysis().isBlank()) {
            GeminiAnalysisResult aiResult = geminiAiService.analyze(ca);
            if (aiResult.isAvailable()) {
                try {
                    String json = objectMapper.writeValueAsString(aiResult);
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    creditAnalysisRepository.saveAiAnalysis(ca.getId(), json, now);
                    ca = creditAnalysisRepository.findLatestByCnpj(normalizedCnpj).orElse(ca);
                } catch (Exception e) {
                    log.warn("Não foi possível persistir análise de IA para CNPJ={}", normalizedCnpj, e);
                }
            }
        }

        return ca;
    }

    private GeminiAnalysisResult parseAiAnalysis(CreditAnalysis ca) {
        if (ca.getAiAnalysis() == null || ca.getAiAnalysis().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(ca.getAiAnalysis(), GeminiAnalysisResult.class);
        } catch (Exception e) {
            log.warn("Não foi possível ler análise de IA para CNPJ={}", ca.getCnpj(), e);
            return null;
        }
    }

    private String buildPdfFileName(CreditAnalysis ca) {
        String baseName = ca.getCompanyName() != null && !ca.getCompanyName().isBlank()
                ? ca.getCompanyName()
                : ca.getCnpj();
        String sanitized = baseName.replaceAll("[\\\\/:*?\"<>|]", "").trim().replaceAll("\\s+", " ");
        return sanitized + ".pdf";
    }

    private CompanyDetail mapUpdateRequestToDomain(CompanyDetailUpdateRequest r) {
        return CompanyDetail.builder()
                .companyName(r.getCompanyName())
                .alias(r.getAlias())
                .founded(r.getFounded())
                .head(r.getHead())
                .statusDate(r.getStatusDate())
                .statusId(r.getStatusId())
                .statusText(r.getStatusText())
                .companyEquity(r.getCompanyEquity())
                .natureId(r.getNatureId())
                .natureText(r.getNatureText())
                .sizeAcronym(r.getSizeAcronym())
                .sizeText(r.getSizeText())
                .street(r.getStreet())
                .number(r.getNumber())
                .details(r.getDetails())
                .district(r.getDistrict())
                .city(r.getCity())
                .state(r.getState())
                .zip(r.getZip())
                .countryId(r.getCountryId())
                .countryName(r.getCountryName())
                .latitude(r.getLatitude())
                .longitude(r.getLongitude())
                .members(r.getMembers())
                .phones(r.getPhones())
                .emails(r.getEmails())
                .mainActivity(r.getMainActivity())
                .sideActivities(r.getSideActivities())
                .build();
    }

    private ClientProfileResponse toProfileResponse(ClientProfileService.ClientProfile profile) {
        java.util.Map<String, PersonAnalysisSummaryResponse> pfSummaries = null;
        java.util.List<CreditAnalysisHistoryItemResponse> historyItems = null;
        if (profile.getPartnerPfAnalyses() != null && !profile.getPartnerPfAnalyses().isEmpty()) {
            pfSummaries = new java.util.HashMap<>();
            for (java.util.Map.Entry<String, PersonAnalysis> entry : profile.getPartnerPfAnalyses().entrySet()) {
                pfSummaries.put(entry.getKey(), personAnalysisDtoMapper.toSummaryResponse(entry.getValue()));
            }
        }
        if (profile.getAnalysisHistory() != null && !profile.getAnalysisHistory().isEmpty()) {
            historyItems = profile.getAnalysisHistory().stream()
                    .map(item -> CreditAnalysisHistoryItemResponse.builder()
                            .id(item.getId())
                            .companyName(item.getCompanyName())
                            .status(item.getStatus() != null ? item.getStatus().toString() : null)
                            .visaoCedente(item.getVisaoCedente())
                            .riskClass(item.getRiskClass())
                            .consultaEm(item.getConsultaEm())
                            .createdAt(item.getCreatedAt())
                            .build())
                    .toList();
        }
        return ClientProfileResponse.builder()
                .client(profile.getClient() != null ? toClientResponse(profile.getClient()) : null)
                .companyDetail(profile.getCompanyDetail() != null ? toResponse(profile.getCompanyDetail()) : null)
                .creditAnalysis(profile.getCreditAnalysis() != null
                        ? creditAnalysisDtoMapper.toResponse(profile.getCreditAnalysis())
                        : null)
                .analysisHistory(historyItems)
                .partnerPfAnalyses(pfSummaries)
                .build();
    }

    private CompanyNoteResponse toCompanyNoteResponse(CompanyNoteService.CompanyNoteView note) {
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

    private CompanyCommercialInformationResponse toCommercialInformationResponse(CompanyCommercialInformationEntity record, UserEntity currentUser) {
        return CompanyCommercialInformationResponse.builder()
                .id(record.getId())
                .authorName(record.getAuthorName())
                .authorEmail(record.getAuthorEmail())
                .canManage(canManageCommercialInformation(record, currentUser))
                .data(record.getOperationDate())
                .tipo(record.getOperationType())
                .parceiro(record.getPartner())
                .clienteDesde(record.getCustomerSince())
                .ultimaOperacaoData(record.getLastOperationDate())
                .ultimaOperacaoValor(record.getLastOperationValue())
                .limite(record.getCreditLimit())
                .riscoDuplicata(record.getDuplicateRisk())
                .riscoCheque(record.getCheckRisk())
                .riscoComissaria(record.getCommissionRisk())
                .vencidosData(record.getOverdueDate())
                .vencidosValorMonetario(record.getOverdueValue())
                .vencidosValor(record.getDuplicateDueDate())
                .vop(record.getVop())
                .pontual(record.getPunctualPercentage())
                .atraso(record.getDelayPercentage())
                .recompra(record.getRepurchasePercentage())
                .cartorio(record.getNotaryPercentage())
                .observacao(record.getNotes())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private CompanyCommercialInformationResponse toCommercialInformationResponse(CompanyCommercialInformationView view, UserEntity currentUser) {
        CompanyCommercialInformationEntity record = view.getRecord();
        ClientEntity client = view.getClient();
        return CompanyCommercialInformationResponse.builder()
                .id(record.getId())
                .cnpj(client != null ? client.getDocumentNumber() : null)
                .empresa(client != null ? client.getName() : null)
                .authorName(record.getAuthorName())
                .authorEmail(record.getAuthorEmail())
                .canManage(canManageCommercialInformation(record, currentUser))
                .data(record.getOperationDate())
                .tipo(record.getOperationType())
                .parceiro(record.getPartner())
                .clienteDesde(record.getCustomerSince())
                .ultimaOperacaoData(record.getLastOperationDate())
                .ultimaOperacaoValor(record.getLastOperationValue())
                .limite(record.getCreditLimit())
                .riscoDuplicata(record.getDuplicateRisk())
                .riscoCheque(record.getCheckRisk())
                .riscoComissaria(record.getCommissionRisk())
                .vencidosData(record.getOverdueDate())
                .vencidosValorMonetario(record.getOverdueValue())
                .vencidosValor(record.getDuplicateDueDate())
                .vop(record.getVop())
                .pontual(record.getPunctualPercentage())
                .atraso(record.getDelayPercentage())
                .recompra(record.getRepurchasePercentage())
                .cartorio(record.getNotaryPercentage())
                .observacao(record.getNotes())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private boolean canManageCommercialInformation(CompanyCommercialInformationEntity record, UserEntity currentUser) {
        return currentUser != null
                && record.getAuthorUserId() != null
                && record.getAuthorUserId().equals(currentUser.getId());
    }

    private UserEntity getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            return null;
        }
        return userRepository.findByEmail(user.getEmail()).orElse(null);
    }

    private ClientResponse toClientResponse(Client c) {
        return ClientResponse.builder()
                .id(c.getId())
                .documentNumber(c.getDocumentNumber())
                .clientCode(c.getClientCode())
                .name(c.getName())
                .email(c.getEmail())
                .phones(c.getPhones())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private CompanyDetailResponse toResponse(CompanyDetail d) {
        return CompanyDetailResponse.builder()
                .id(d.getId())
                .documentNumber(d.getDocumentNumber())
                .updatedAt(d.getUpdatedAt())
                .alias(d.getAlias())
                .founded(d.getFounded())
                .head(d.getHead())
                .statusDate(d.getStatusDate())
                .statusId(d.getStatusId())
                .statusText(d.getStatusText())
                .companyId(d.getCompanyId())
                .companyName(d.getCompanyName())
                .companyEquity(d.getCompanyEquity())
                .natureId(d.getNatureId())
                .natureText(d.getNatureText())
                .sizeAcronym(d.getSizeAcronym())
                .sizeText(d.getSizeText())
                .street(d.getStreet())
                .number(d.getNumber())
                .details(d.getDetails())
                .district(d.getDistrict())
                .city(d.getCity())
                .state(d.getState())
                .zip(d.getZip())
                .countryId(d.getCountryId())
                .countryName(d.getCountryName())
                .latitude(d.getLatitude())
                .longitude(d.getLongitude())
                .members(d.getMembers())
                .phones(d.getPhones())
                .emails(d.getEmails())
                .mainActivity(d.getMainActivity())
                .sideActivities(d.getSideActivities())
                .createdAt(d.getCreatedAt())
                .modifiedAt(d.getModifiedAt())
                .build();
    }
}
