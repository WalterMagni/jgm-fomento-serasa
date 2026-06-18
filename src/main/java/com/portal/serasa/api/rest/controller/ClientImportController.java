package com.portal.serasa.api.rest.controller;

import com.opencsv.exceptions.CsvException;
import com.portal.serasa.application.service.ClientImportService;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.integration.csv.CsvClientReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/clients/import")
@RequiredArgsConstructor
public class ClientImportController {

    private static final int DEFAULT_PREVIEW_LIMIT = 20;

    @Value("${app.client.import.default-path:clientes.csv}")
    private String defaultCsvPath;

    private final ClientImportService clientImportService;
    private final CsvClientReader csvClientReader;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientImportService.ClientImportResult> importFromUpload(
            @RequestParam("file") MultipartFile file) throws IOException, CsvException {
        try (InputStream is = file.getInputStream()) {
            var result = clientImportService.importFromCsv(is);
            return ResponseEntity.ok(result);
        }
    }

    @PostMapping(value = "/codigos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ClientImportService.CodeImportResult> importCodesOnly(
            @RequestParam("file") MultipartFile file) throws IOException, CsvException {
        try (InputStream is = file.getInputStream()) {
            return ResponseEntity.ok(clientImportService.importCodesOnly(is));
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CsvClientReader.CsvClientPreview> previewFromUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "20") int limit) throws IOException, CsvException {
        try (InputStream is = file.getInputStream()) {
            var preview = clientImportService.previewFromCsv(is, Math.min(limit, 100));
            return ResponseEntity.ok(preview);
        }
    }

    @GetMapping("/preview")
    public ResponseEntity<CsvClientReader.CsvClientPreview> previewFromFile(
            @RequestParam(defaultValue = "20") int limit) throws IOException, CsvException {
        Path path = Path.of(defaultCsvPath);
        if (!Files.exists(path)) {
            throw new EntityNotFoundException("Arquivo CSV não encontrado: " + defaultCsvPath);
        }
        try (InputStream is = new FileInputStream(path.toFile())) {
            var preview = clientImportService.previewFromCsv(is, Math.min(limit, 100));
            return ResponseEntity.ok(preview);
        }
    }

    @PostMapping("/from-file")
    public ResponseEntity<ClientImportService.ClientImportResult> importFromFile() throws IOException, CsvException {
        Path path = Path.of(defaultCsvPath);
        if (!Files.exists(path)) {
            throw new EntityNotFoundException("Arquivo CSV não encontrado: " + defaultCsvPath);
        }
        try (InputStream is = new FileInputStream(path.toFile())) {
            var result = clientImportService.importFromCsv(is);
            return ResponseEntity.ok(result);
        }
    }
}
