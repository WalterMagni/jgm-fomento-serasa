package com.portal.serasa.application.service;

import com.portal.serasa.api.rest.dto.response.DocumentStorageSettingsResponse;
import com.portal.serasa.infrastructure.persistence.entity.SystemSettingEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    public static final String DOCUMENT_STORAGE_BASE_PATH_KEY = "company.documents.base-path";

    private final SystemSettingRepository systemSettingRepository;

    @Transactional(readOnly = true)
    public DocumentStorageSettingsResponse getDocumentStorageSettings() {
        return systemSettingRepository.findById(DOCUMENT_STORAGE_BASE_PATH_KEY)
                .map(setting -> DocumentStorageSettingsResponse.builder()
                        .configured(setting.getValue() != null && !setting.getValue().isBlank())
                        .basePath(setting.getValue())
                        .updatedByName(setting.getUpdatedByName())
                        .updatedAt(setting.getUpdatedAt())
                        .build())
                .orElse(DocumentStorageSettingsResponse.builder()
                        .configured(false)
                        .build());
    }

    @Transactional
    public DocumentStorageSettingsResponse updateDocumentStorageBasePath(String rawPath, UserEntity currentUser) {
        Path basePath = Path.of(rawPath.trim()).toAbsolutePath().normalize();
        if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
            throw new IllegalArgumentException("A pasta base informada não existe ou não é um diretório");
        }

        Path realBasePath;
        try {
            realBasePath = basePath.toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível acessar a pasta base informada");
        }

        SystemSettingEntity setting = systemSettingRepository.findById(DOCUMENT_STORAGE_BASE_PATH_KEY)
                .orElseGet(() -> SystemSettingEntity.builder().key(DOCUMENT_STORAGE_BASE_PATH_KEY).build());
        setting.setValue(realBasePath.toString());
        setting.setUpdatedByUserId(currentUser.getId());
        setting.setUpdatedByName(currentUser.getName());
        setting.setUpdatedByEmail(currentUser.getEmail());
        systemSettingRepository.save(setting);

        return DocumentStorageSettingsResponse.builder()
                .configured(true)
                .basePath(setting.getValue())
                .updatedByName(setting.getUpdatedByName())
                .updatedAt(setting.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Path requireDocumentStorageBasePath() {
        String value = systemSettingRepository.findById(DOCUMENT_STORAGE_BASE_PATH_KEY)
                .map(SystemSettingEntity::getValue)
                .filter(path -> !path.isBlank())
                .orElseThrow(() -> new IllegalArgumentException("Configure a pasta base dos documentos em Sistema"));

        try {
            return Path.of(value).toAbsolutePath().normalize().toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("A pasta base configurada não está acessível");
        }
    }
}
