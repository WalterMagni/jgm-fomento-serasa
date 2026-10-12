package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoCardEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoCardEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cor e etiquetas do card — organização visual do quadro, como na esteira de liberação.
 *
 * <p>Não passa pela trava de versão nem pela timeline: trocar a cor não pode derrubar o formulário
 * que outra pessoa está editando, e "pintou de verde" não é história do cliente. Qualquer pessoa da
 * equipe organiza; apagar uma etiqueta da esteira inteira é do admin, porque some de todo card.</p>
 */
@Service
@RequiredArgsConstructor
public class ProspeccaoOrganizacaoService {

    static final int LIMITE_NOME = 40;

    private final ProspeccaoService prospeccaoService;
    private final ProspeccaoAutorizacao autorizacao;
    private final ProspeccaoJpaRepository prospeccaoRepository;
    private final ProspeccaoEtiquetaJpaRepository etiquetaRepository;
    private final ProspeccaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    private final ApplicationEventPublisher acontecimentos;

    @Transactional(readOnly = true)
    public List<ProspeccaoEtiquetaEntity> etiquetas() {
        return etiquetaRepository.findAllByOrderByNomeAsc();
    }

    /** Criar com nome que já existe devolve a existente: duas "Cliente novo" não ajudam ninguém. */
    @Transactional
    public ProspeccaoEtiquetaEntity criarEtiqueta(String nome, CorLiberacao cor, UserEntity autor) {
        autorizacao.exigirCriador(autor);
        String limpo = nomeValido(nome);
        return etiquetaRepository.findByNomeIgnoreCase(limpo).orElseGet(() -> etiquetaRepository.save(
                ProspeccaoEtiquetaEntity.builder()
                        .nome(limpo)
                        .cor(cor != null ? cor : CorLiberacao.CINZA)
                        .criadoPorNome(autor.getName())
                        .criadoEm(LocalDateTime.now())
                        .build()));
    }

    @Transactional
    public void apagarEtiqueta(UUID id, UserEntity autor) {
        autorizacao.exigirAdmin(autor);
        etiquetaRepository.deleteById(id);
    }

    @Transactional
    public void definirEtiquetas(UUID cardId, Collection<UUID> ids, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(cardId);
        autorizacao.exigirEscrita(card, autor);
        Set<UUID> novas = new LinkedHashSet<>(ids == null ? List.of() : ids);
        if (etiquetaRepository.findAllById(novas).size() != novas.size()) {
            throw new EntityNotFoundException("Etiqueta não encontrada");
        }
        cardEtiquetaRepository.apagarDoCard(cardId);
        novas.forEach(etiquetaId -> cardEtiquetaRepository.save(new ProspeccaoCardEtiquetaEntity(cardId, etiquetaId)));
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.Alterado(card, autor));
    }

    @Transactional
    public void definirCor(UUID cardId, CorLiberacao cor, UserEntity autor) {
        ProspeccaoEntity card = prospeccaoService.buscar(cardId);
        autorizacao.exigirEscrita(card, autor);
        card.setCor(cor);
        prospeccaoRepository.save(card);
        acontecimentos.publishEvent(new ProspeccaoAcontecimento.Alterado(card, autor));
    }

    /** Etiquetas de cada card, numa consulta só para o quadro inteiro. */
    @Transactional(readOnly = true)
    public Map<UUID, List<ProspeccaoEtiquetaEntity>> etiquetasPorCard(Collection<UUID> cardIds) {
        if (cardIds.isEmpty()) {
            return Map.of();
        }
        List<ProspeccaoCardEtiquetaEntity> vinculos = cardEtiquetaRepository.findByProspeccaoIdIn(cardIds);
        Map<UUID, ProspeccaoEtiquetaEntity> porId = etiquetaRepository
                .findAllById(vinculos.stream().map(ProspeccaoCardEtiquetaEntity::getEtiquetaId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProspeccaoEtiquetaEntity::getId, Function.identity()));
        return vinculos.stream()
                .filter(vinculo -> porId.containsKey(vinculo.getEtiquetaId()))
                .collect(Collectors.groupingBy(ProspeccaoCardEtiquetaEntity::getProspeccaoId,
                        Collectors.mapping(vinculo -> porId.get(vinculo.getEtiquetaId()), Collectors.toList())));
    }

    private static String nomeValido(String nome) {
        String limpo = nome == null ? "" : nome.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) {
            throw new IllegalArgumentException("Dê um nome à etiqueta.");
        }
        if (limpo.length() > LIMITE_NOME) {
            throw new IllegalArgumentException("Etiqueta passa de " + LIMITE_NOME + " caracteres.");
        }
        return limpo;
    }
}
