package com.portal.serasa.api.rest.controller;

import com.portal.serasa.api.rest.mapper.LiberacaoResponseAssembler;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import com.portal.serasa.infrastructure.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Quem existe no portal, para escolher destinatário de pendência e, depois, para as menções.
 *
 * <p>Aberto a todo usuário autenticado, ao contrário de {@code GET /api/auth/users}, que é do
 * admin. Por isso não devolve e-mail nem data de cadastro: só o que a tela precisa para mostrar
 * e escolher uma pessoa.</p>
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioDiretorioController {

    private final UserRepository userRepository;
    private final UsuarioLogado usuarioLogado;

    public record PessoaDiretorio(UUID id, String nome, String iniciais, boolean analista, boolean comite) {
    }

    @GetMapping("/diretorio")
    public ResponseEntity<List<PessoaDiretorio>> diretorio() {
        usuarioLogado.obter();
        return ResponseEntity.ok(userRepository.findAllByOrderByNameAsc().stream()
                .map(user -> new PessoaDiretorio(user.getId(), user.getName(),
                        LiberacaoResponseAssembler.iniciais(user.getName()), user.isAnalista(), user.isComite()))
                .toList());
    }
}
