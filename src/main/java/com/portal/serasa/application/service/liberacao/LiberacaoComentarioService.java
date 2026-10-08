package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoComentarioJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Comentários do card.
 *
 * <p>Qualquer usuário comenta em qualquer etapa — é por aqui que a auxiliar responde quando a
 * analista a marca. Editar e apagar é só do autor: comentário alheio é fala de outra pessoa.</p>
 */
@Service
@RequiredArgsConstructor
public class LiberacaoComentarioService {

    static final int LIMITE_TEXTO = 10_000;

    private final LiberacaoComentarioJpaRepository comentarioRepository;
    private final LiberacaoService liberacaoService;
    private final LiberacaoAutorizacao autorizacao;

    @Transactional(readOnly = true)
    public List<LiberacaoComentarioEntity> listar(UUID cardId) {
        return comentarioRepository.findByCardIdAndExcluidoEmIsNullOrderByCriadoEmDesc(cardId);
    }

    @Transactional
    public LiberacaoComentarioEntity comentar(UUID cardId, String texto, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        return comentarioRepository.save(LiberacaoComentarioEntity.builder()
                .cardId(card.getId())
                .autorId(autor.getId())
                .autorNome(autor.getName())
                .texto(validar(texto))
                .criadoEm(LocalDateTime.now())
                .build());
    }

    /** Devolve o texto anterior junto, para quem precisar saber quem passou a ser mencionado. */
    public record Edicao(LiberacaoComentarioEntity comentario, String textoAnterior) {
    }

    @Transactional
    public Edicao editar(UUID cardId, UUID comentarioId, String texto, UserEntity autor) {
        LiberacaoComentarioEntity comentario = doAutor(cardId, comentarioId, autor);
        String anterior = comentario.getTexto();
        String novo = validar(texto);
        if (!novo.equals(anterior)) {
            comentario.setTexto(novo);
            comentario.setEditadoEm(LocalDateTime.now());
            comentario = comentarioRepository.save(comentario);
        }
        return new Edicao(comentario, anterior);
    }

    @Transactional
    public void apagar(UUID cardId, UUID comentarioId, UserEntity autor) {
        LiberacaoComentarioEntity comentario = doAutor(cardId, comentarioId, autor);
        comentario.setExcluidoEm(LocalDateTime.now());
        comentarioRepository.save(comentario);
    }

    private LiberacaoComentarioEntity doAutor(UUID cardId, UUID comentarioId, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        liberacaoService.buscar(cardId);
        LiberacaoComentarioEntity comentario = comentarioRepository.findById(comentarioId)
                .filter(encontrado -> encontrado.getCardId().equals(cardId) && encontrado.getExcluidoEm() == null)
                .orElseThrow(() -> new EntityNotFoundException("Comentário não encontrado"));
        if (!autor.getId().equals(comentario.getAutorId())) {
            throw new AcessoNegadoException("Só " + comentario.getAutorNome() + " altera este comentário.");
        }
        return comentario;
    }

    private static String validar(String texto) {
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Escreva o comentário.");
        }
        if (limpo.length() > LIMITE_TEXTO) {
            throw new IllegalArgumentException("Comentário passa de " + LIMITE_TEXTO + " caracteres.");
        }
        return limpo;
    }
}
