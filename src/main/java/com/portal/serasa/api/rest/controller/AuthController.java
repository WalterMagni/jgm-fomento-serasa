package com.portal.serasa.api.rest.controller;

import com.portal.serasa.infrastructure.security.JwtUtil;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${app.user-management.allowed-emails:}")
    private String userManagementAllowedEmails;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas"));
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "name", user.getName(),
                "email", user.getEmail(),
                "emailNotificacaoCedente", user.isEmailNotificacaoCedente(),
                "canManageUsers", canManageUsers(user.getEmail())));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email já cadastrado"));
        }

        UserEntity newUser = UserEntity.builder()
                .name(name)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role("ROLE_USER")
                .emailNotificacaoCedente(true)
                .build();

        userRepository.save(newUser);
        return ResponseEntity.status(201).body(Map.of("message", "Usuário criado com sucesso"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "email", user.getEmail(),
                "emailNotificacaoCedente", user.isEmailNotificacaoCedente(),
                "canManageUsers", canManageUsers(user.getEmail())));
    }

    @PatchMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity currentUser)) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }

        UserEntity user = userRepository.findByEmail(currentUser.getEmail())
                .orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuário não encontrado"));
        }

        if (body.containsKey("name") && body.get("name") != null) {
            user.setName(body.get("name").toString());
        }
        if (body.containsKey("emailNotificacaoCedente") && body.get("emailNotificacaoCedente") != null) {
            user.setEmailNotificacaoCedente(Boolean.parseBoolean(body.get("emailNotificacaoCedente").toString()));
        }

        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
                "name", user.getName(),
                "email", user.getEmail(),
                "emailNotificacaoCedente", user.isEmailNotificacaoCedente(),
                "canManageUsers", canManageUsers(user.getEmail())));
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        if (!canManageUsers(currentUser.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("error", "Sem permissão para gerenciar usuários"));
        }

        List<Map<String, Object>> users = userRepository.findAll().stream()
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(user -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", user.getId());
                    item.put("name", user.getName());
                    item.put("email", user.getEmail());
                    item.put("role", user.getRole());
                    item.put("emailNotificacaoCedente", user.isEmailNotificacaoCedente());
                    item.put("createdAt", user.getCreatedAt());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(users);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        if (!canManageUsers(currentUser.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("error", "Sem permissão para apagar usuários"));
        }

        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "ID de usuário inválido"));
        }

        UserEntity user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Usuário não encontrado"));
        }
        if (user.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Você não pode apagar o seu próprio usuário"));
        }

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "Usuário removido com sucesso"));
    }

    private UserEntity getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserEntity user)) {
            return null;
        }
        return userRepository.findByEmail(user.getEmail()).orElse(null);
    }

    private boolean canManageUsers(String email) {
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
