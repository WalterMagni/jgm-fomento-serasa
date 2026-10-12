package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.liberacao.LiberacaoService;
import com.portal.serasa.application.service.liberacao.MencaoParser;
import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoComentarioJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Comentários do card, com menção por {@code @}. Mesmas regras da esteira de liberação: qualquer
 * pessoa comenta; editar e apagar é só do autor, porque comentário alheio é fala de outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class ProspeccaoComentarioService {

    static final int LIMITE_TEXTO = 10_000;

    private final ProspeccaoComentarioJpaRepository comentarioRepository;
    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ApplicationEventPublisher acontecimentos;

    @Transactional(readOnly = true)
    public List<ProspeccaoComentarioEntity> listar(UUID cardId) {
        return comentarioRepository.findByProspeccaoIdAndExcluidoEmIsNullOrderByCriadoEmDesc(cardId);
    }

    @Transactional
    public ProspeccaoComentarioEntity comentar(UUID cardId, String texto, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(cardId);
        autorizacao.exigirEscrita(card, autor);
        ProspeccaoComentarioEntity salvo = comentarioRepository.save(ProspeccaoComentarioEntity.builder()
                .prospeccaoId(cardId)
                .autorId(autor.getId())
                .autorNome(autor.getName())
                .texto(validar(texto))
                .criadoEm(LocalDateTime.now())
                .build());
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.Comentado(card, false, autor,
                MencaoParser.usuarios(salvo.getTexto()), LiberacaoService.trecho(salvo.getTexto())));
        return salvo;
    }

    @Transactional
    public ProspeccaoComentarioEntity editar(UUID cardId, UUID comentarioId, String texto, UserEntity autor) {
        ProspeccaoComentarioEntity comentario = doAutor(cardId, comentarioId, autor);
        String anterior = comentario.getTexto();
        String novo = validar(texto);
        if (novo.equals(anterior)) {
            return comentario;
        }
        comentario.setTexto(novo);
        comentario.setEditadoEm(LocalDateTime.now());
        ProspeccaoComentarioEntity salvo = comentarioRepository.save(comentario);
        Set<UUID> novosMencionados = MencaoParser.novosUsuarios(anterior, novo);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.Comentado(prospeccaoService.buscar(cardId), true, autor,
                novosMencionados, LiberacaoService.trecho(novo)));
        return salvo;
    }

    @Transactional
    public void apagar(UUID cardId, UUID comentarioId, UserEntity autor) {
        ProspeccaoComentarioEntity comentario = doAutor(cardId, comentarioId, autor);
        comentario.setExcluidoEm(LocalDateTime.now());
        comentarioRepository.save(comentario);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.Alterado(prospeccaoService.buscar(cardId), autor));
    }

    private ProspeccaoComentarioEntity doAutor(UUID cardId, UUID comentarioId, UserEntity autor) {
        ProspeccaoComentarioEntity comentario = comentarioRepository.findById(comentarioId)
                .filter(encontrado -> encontrado.getProspeccaoId().equals(cardId) && encontrado.getExcluidoEm() == null)
                .orElseThrow(() -> new EntityNotFoundException("Comentário não encontrado"));
        if (autor == null || !autor.getId().equals(comentario.getAutorId())) {
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
