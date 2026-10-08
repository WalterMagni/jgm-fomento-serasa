package com.portal.serasa.application.service.usuario;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Marcas de analista e Comitê. Só o admin chama — a checagem fica no controller. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioPapelService {

    private final UserRepository userRepository;
    private final LiberacaoParecerJpaRepository parecerRepository;

    /**
     * Grava as marcas do usuário.
     *
     * <p>Quem sai do Comitê deixa de dever parecer nos cards que estão no Comitê agora. Sem isso, o
     * card ficaria travado esperando alguém que não tem mais por que responder — e ninguém
     * conseguiria destravar pela tela.</p>
     */
    @Transactional
    public UserEntity definir(UUID usuarioId, boolean analista, boolean comite, UserEntity admin) {
        if (comite && !analista) {
            throw new IllegalArgumentException("Membro do Comitê precisa ser analista.");
        }
        UserEntity usuario = userRepository.findById(usuarioId)
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        boolean saiuDoComite = usuario.isComite() && !comite;
        usuario.setAnalista(analista);
        usuario.setComite(comite);
        userRepository.save(usuario);

        if (saiuDoComite) {
            int liberados = parecerRepository.apagarAguardandoDoUsuario(usuarioId);
            if (liberados > 0) {
                log.info("{} saiu do Comitê; {} parecer(es) pendente(s) removido(s)", usuario.getName(), liberados);
            }
        }
        log.info("Papéis de {} alterados por {}: analista={}, comite={}",
                usuario.getEmail(), admin.getEmail(), analista, comite);
        return usuario;
    }
}
