package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.dto.request.DocumentStorageSettingsRequest;
import com.portal.serasa.application.service.SystemSettingService;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/document-storage")
@RequiredArgsConstructor
public class DocumentStorageController {

    private final SystemSettingService systemSettingService;
    private final UserRepository userRepository;

    @Value("${app.user-management.allowed-emails:}")
    private String userManagementAllowedEmails;

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        return ResponseEntity.ok(systemSettingService.getDocumentStorageSettings());
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(@Valid @RequestBody DocumentStorageSettingsRequest request) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        if (!canManageSystemSettings(currentUser.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("error", "Sem permissão para alterar configurações do sistema"));
        }

        return ResponseEntity.ok(systemSettingService.updateDocumentStorageBasePath(request.getBasePath(), currentUser));
    }

    private UserEntity getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            return null;
        }
        return userRepository.findByEmail(user.getEmail()).orElse(null);
    }

    private boolean canManageSystemSettings(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return Arrays.stream(userManagementAllowedEmails.split("[,;]"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(email.trim().toLowerCase(Locale.ROOT)));
    }
}
