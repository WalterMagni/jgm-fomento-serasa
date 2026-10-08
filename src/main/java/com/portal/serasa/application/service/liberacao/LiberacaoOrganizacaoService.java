package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Os recursos do Trello que não são campo da operação: etiquetas, cor e membros.
 *
 * <p>Cor e etiquetas seguem a mesma regra de edição do card (Origem todos, Comitê em diante só
 * analista) e ficam no histórico, mas não passam pela versão: trocar a cor não pode derrubar o
 * formulário que outra pessoa está editando. Acompanhar o card é escolha de cada um — qualquer
 * pessoa segue ou deixa de seguir a si mesma em qualquer etapa.</p>
 */
@Service
@RequiredArgsConstructor
public class LiberacaoOrganizacaoService {

    static final int LIMITE_NOME_ETIQUETA = 40;

    private final LiberacaoService liberacaoService;
    private final LiberacaoAutorizacao autorizacao;
    private final LiberacaoCardJpaRepository cardRepository;
    private final LiberacaoEtiquetaJpaRepository etiquetaRepository;
    private final LiberacaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    private final LiberacaoMembroJpaRepository membroRepository;
    private final UserRepository userRepository;

    // ------------------------------------------------------------ etiquetas

    @Transactional(readOnly = true)
    public List<LiberacaoEtiquetaEntity> etiquetas() {
        return etiquetaRepository.findAllByOrderByNomeAsc();
    }

    /** Criar com nome que já existe devolve a existente: duas "Urgente" no quadro não ajudam ninguém. */
    @Transactional
    public LiberacaoEtiquetaEntity criarEtiqueta(String nome, CorLiberacao cor, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        String limpo = nomeValido(nome);
        return etiquetaRepository.findByNomeIgnoreCase(limpo).orElseGet(() -> etiquetaRepository.save(
                LiberacaoEtiquetaEntity.builder()
                        .nome(limpo)
                        .cor(cor != null ? cor : CorLiberacao.CINZA)
                        .criadoPorNome(autor.getName())
                        .criadoEm(LocalDateTime.now())
                        .build()));
    }

    /** Renomear ou recolorir muda a etiqueta em todo card que a usa — por isso é de analista. */
    @Transactional
    public LiberacaoEtiquetaEntity editarEtiqueta(UUID id, String nome, CorLiberacao cor, UserEntity autor) {
        autorizacao.exigirAnalista(autor, "alterar etiqueta");
        LiberacaoEtiquetaEntity etiqueta = etiquetaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Etiqueta não encontrada"));
        String limpo = nomeValido(nome);
        etiquetaRepository.findByNomeIgnoreCase(limpo)
                .filter(outra -> !outra.getId().equals(id))
                .ifPresent(outra -> {
                    throw new IllegalArgumentException("Já existe a etiqueta " + outra.getNome() + ".");
                });
        etiqueta.setNome(limpo);
        if (cor != null) {
            etiqueta.setCor(cor);
        }
        return etiquetaRepository.save(etiqueta);
    }

    @Transactional
    public void apagarEtiqueta(UUID id, UserEntity autor) {
        autorizacao.exigirAnalista(autor, "apagar etiqueta");
        etiquetaRepository.deleteById(id);
    }

    @Transactional
    public LiberacaoCardEntity definirEtiquetas(UUID cardId, Collection<UUID> ids, UserEntity autor) {
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        autorizacao.exigirEdicao(card, autor);

        Set<UUID> novas = new LinkedHashSet<>(ids == null ? List.of() : ids);
        List<LiberacaoEtiquetaEntity> encontradas = etiquetaRepository.findAllById(novas);
        if (encontradas.size() != novas.size()) {
            throw new EntityNotFoundException("Etiqueta não encontrada");
        }
        Set<UUID> atuais = cardEtiquetaRepository.findByCardId(cardId).stream()
                .map(LiberacaoCardEtiquetaEntity::getEtiquetaId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (atuais.equals(novas)) {
            return card;
        }

        String antes = nomes(etiquetaRepository.findAllById(atuais));
        cardEtiquetaRepository.apagarDoCard(cardId);
        novas.forEach(etiquetaId -> cardEtiquetaRepository.save(new LiberacaoCardEtiquetaEntity(cardId, etiquetaId)));

        liberacaoService.registrarEdicao(card, "etiquetas", antes, nomes(encontradas), autor);
        return tocar(card, autor);
    }

    // ------------------------------------------------------------------ cor

    @Transactional
    public LiberacaoCardEntity definirCor(UUID cardId, CorLiberacao cor, UserEntity autor) {
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        autorizacao.exigirEdicao(card, autor);
        if (card.getCor() == cor) {
            return card;
        }
        String antes = rotulo(card.getCor());
        cardRepository.definirCor(cardId, cor == null ? null : cor.name());
        liberacaoService.registrarEdicao(card, "cor", antes, rotulo(cor), autor);
        return tocar(card, autor);
    }

    // -------------------------------------------------------------- membros

    @Transactional
    public LiberacaoCardEntity adicionarMembro(UUID cardId, UUID usuarioId, UserEntity autor) {
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        exigirMexerEmMembro(card, usuarioId, autor);
        if (!userRepository.existsById(usuarioId)) {
            throw new EntityNotFoundException("Usuário não encontrado");
        }
        liberacaoService.acompanhar(cardId, usuarioId, OrigemMembro.MANUAL);
        liberacaoService.avisarEdicao(card, autor);
        return card;
    }

    /**
     * Deixar de acompanhar não tira a pessoa de nenhuma obrigação: o parecer do Comitê e a
     * pendência continuam dela, e o aviso de decisão chega ao criador de qualquer jeito.
     */
    @Transactional
    public LiberacaoCardEntity removerMembro(UUID cardId, UUID usuarioId, UserEntity autor) {
        LiberacaoCardEntity card = liberacaoService.buscar(cardId);
        exigirMexerEmMembro(card, usuarioId, autor);
        membroRepository.deleteById(new LiberacaoMembroEntity.Chave(cardId, usuarioId));
        liberacaoService.avisarEdicao(card, autor);
        return card;
    }

    private void exigirMexerEmMembro(LiberacaoCardEntity card, UUID usuarioId, UserEntity autor) {
        autorizacao.exigirAutenticado(autor);
        boolean siMesmo = autor.getId().equals(usuarioId);
        if (!siMesmo && !autorizacao.podeEditar(card, autor)) {
            throw new AcessoNegadoException("A partir do Comitê, só analista muda os membros de outra pessoa.");
        }
    }

    // ------------------------------------------------------------- internos

    private LiberacaoCardEntity tocar(LiberacaoCardEntity card, UserEntity autor) {
        cardRepository.tocarSemVersao(card.getId(), LocalDateTime.now(), autor.getId(), autor.getName());
        LiberacaoCardEntity atualizado = liberacaoService.buscar(card.getId());
        liberacaoService.avisarEdicao(atualizado, autor);
        return atualizado;
    }

    private static String nomeValido(String nome) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Dê um nome à etiqueta.");
        }
        if (limpo.length() > LIMITE_NOME_ETIQUETA) {
            throw new IllegalArgumentException("Etiqueta com até " + LIMITE_NOME_ETIQUETA + " caracteres.");
        }
        return limpo;
    }

    private static String nomes(Collection<LiberacaoEtiquetaEntity> etiquetas) {
        String juntas = etiquetas.stream().map(LiberacaoEtiquetaEntity::getNome).sorted().collect(Collectors.joining(", "));
        return juntas.isEmpty() ? "—" : juntas;
    }

    static String rotulo(CorLiberacao cor) {
        if (cor == null) {
            return "sem cor";
        }
        return switch (cor) {
            case VERMELHO -> "vermelho";
            case LARANJA -> "laranja";
            case AMARELO -> "amarelo";
            case VERDE -> "verde";
            case AZUL -> "azul";
            case ROXO -> "roxo";
            case ROSA -> "rosa";
            case CINZA -> "cinza";
        };
    }
}
