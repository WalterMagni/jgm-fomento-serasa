package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoCardEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoOrganizacaoServiceTest {

    @Mock private ProspeccaoService prospeccaoService;
    @Mock private ProspeccaoJpaRepository prospeccaoRepository;
    @Mock private ProspeccaoEtiquetaJpaRepository etiquetaRepository;
    @Mock private ProspeccaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    @Mock private ApplicationEventPublisher acontecimentos;

    private ProspeccaoOrganizacaoService service;
    private final UserEntity analista = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").role("ROLE_USER").build();
    private final ProspeccaoEntity card = ProspeccaoEntity.builder().id(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        service = new ProspeccaoOrganizacaoService(prospeccaoService, new ProspeccaoAutorizacao(), prospeccaoRepository,
                etiquetaRepository, cardEtiquetaRepository, acontecimentos);
        when(prospeccaoService.buscar(card.getId())).thenReturn(card);
        when(etiquetaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("criar etiqueta com nome que já existe devolve a existente")
    void shouldReuseExistingLabel() {
        ProspeccaoEtiquetaEntity existente = ProspeccaoEtiquetaEntity.builder().id(UUID.randomUUID()).nome("Cliente novo")
                .cor(CorLiberacao.VERDE).build();
        when(etiquetaRepository.findByNomeIgnoreCase("cliente novo")).thenReturn(Optional.of(existente));

        assertThat(service.criarEtiqueta("  cliente   novo ", CorLiberacao.AZUL, analista)).isSameAs(existente);
        verify(etiquetaRepository, never()).save(any());
    }

    @Test
    @DisplayName("etiqueta sem nome ou longa demais não passa")
    void shouldValidateName() {
        assertThatThrownBy(() -> service.criarEtiqueta("  ", CorLiberacao.AZUL, analista)).hasMessageContaining("nome");
        assertThatThrownBy(() -> service.criarEtiqueta("x".repeat(41), CorLiberacao.AZUL, analista)).hasMessageContaining("40");
    }

    @Test
    @DisplayName("marcar etiqueta inexistente não passa; apagar etiqueta é do admin")
    void shouldGuardLabels() {
        UUID fantasma = UUID.randomUUID();
        when(etiquetaRepository.findAllById(Set.of(fantasma))).thenReturn(List.of());
        assertThatThrownBy(() -> service.definirEtiquetas(card.getId(), List.of(fantasma), analista))
                .isInstanceOf(EntityNotFoundException.class);

        assertThatThrownBy(() -> service.apagarEtiqueta(fantasma, analista)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("cor do card: grava e avisa o quadro dos outros")
    void shouldSetColor() {
        service.definirCor(card.getId(), CorLiberacao.ROXO, analista);

        assertThat(card.getCor()).isEqualTo(CorLiberacao.ROXO);
        verify(acontecimentos).publishEvent(any(ProspeccaoAcontecimento.Alterado.class));
    }
}
