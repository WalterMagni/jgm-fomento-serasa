package com.portal.serasa.api.rest.controller;

import com.portal.serasa.application.service.usuario.UsuarioPapelService;
import com.portal.serasa.infrastructure.security.AdminAllowList;
import com.portal.serasa.infrastructure.security.JwtUtil;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private final AdminAllowList adminAllowList;
    private final UsuarioPapelService usuarioPapelService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        UserEntity user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            return ResponseEntity.status(401).body(Map.of("error", "Credenciais inválidas"));
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        Map<String, Object> resposta = perfil(user);
        resposta.put("token", token);
        return ResponseEntity.ok(resposta);
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
        // O principal vem do filtro desta mesma requisição, mas relido do banco: as marcas de
        // analista podem ter mudado desde o login.
        return ResponseEntity.ok(perfil(userRepository.findByEmail(user.getEmail()).orElse(user)));
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
        if (body.containsKey("somNotificacao") && body.get("somNotificacao") != null) {
            user.setSomNotificacao(Boolean.parseBoolean(body.get("somNotificacao").toString()));
        }
        if (body.containsKey("emailLiberacao") && body.get("emailLiberacao") != null) {
            user.setEmailLiberacao(Boolean.parseBoolean(body.get("emailLiberacao").toString()));
        }

        userRepository.save(user);

        return ResponseEntity.ok(perfil(user));
    }

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        if (!adminAllowList.contem(currentUser.getEmail())) {
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
                    item.put("analista", user.isAnalista());
                    item.put("comite", user.isComite());
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
        if (!adminAllowList.contem(currentUser.getEmail())) {
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

    /**
     * Marcas da esteira de liberação. Só admin.
     *
     * <p>Admin pode marcar a si mesmo: o dono da empresa é admin e também analista.</p>
     */
    @PatchMapping("/users/{id}/papeis")
    public ResponseEntity<?> updateRoles(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        UserEntity currentUser = getAuthenticatedUser();
        if (currentUser == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Não autenticado"));
        }
        if (!adminAllowList.contem(currentUser.getEmail())) {
            return ResponseEntity.status(403).body(Map.of("error", "Sem permissão para alterar papéis"));
        }
        boolean analista = Boolean.TRUE.equals(body.get("analista"));
        boolean comite = Boolean.TRUE.equals(body.get("comite"));
        UserEntity user = usuarioPapelService.definir(id, analista, comite, currentUser);
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "analista", user.isAnalista(),
                "comite", user.isComite()));
    }

    private Map<String, Object> perfil(UserEntity user) {
        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("id", user.getId());
        perfil.put("name", user.getName());
        perfil.put("email", user.getEmail());
        perfil.put("emailNotificacaoCedente", user.isEmailNotificacaoCedente());
        perfil.put("canManageUsers", adminAllowList.contem(user.getEmail()));
        perfil.put("analista", user.isAnalista());
        perfil.put("comite", user.isComite());
        perfil.put("somNotificacao", user.isSomNotificacao());
        perfil.put("emailLiberacao", user.isEmailLiberacao());
        return perfil;
    }
}
