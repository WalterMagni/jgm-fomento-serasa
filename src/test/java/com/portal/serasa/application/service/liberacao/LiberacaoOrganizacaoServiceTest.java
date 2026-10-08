package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.model.liberacao.CorLiberacao;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoEtiquetaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoCardJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoEtiquetaJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiberacaoOrganizacaoServiceTest {

    @Mock private LiberacaoService liberacaoService;
    @Spy private LiberacaoAutorizacao autorizacao = new LiberacaoAutorizacao();
    @Mock private LiberacaoCardJpaRepository cardRepository;
    @Mock private LiberacaoEtiquetaJpaRepository etiquetaRepository;
    @Mock private LiberacaoCardEtiquetaJpaRepository cardEtiquetaRepository;
    @Mock private LiberacaoMembroJpaRepository membroRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private LiberacaoOrganizacaoService service;

    private UserEntity aline;
    private UserEntity andressa;
    private LiberacaoCardEntity card;

    @BeforeEach
    void setUp() {
        aline = UserEntity.builder().id(UUID.randomUUID()).name("Aline").build();
        andressa = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").analista(true).build();
        card = LiberacaoCardEntity.builder().id(UUID.randomUUID()).numero(7L).etapa(EtapaLiberacao.ORIGEM).build();
        when(liberacaoService.buscar(card.getId())).thenReturn(card);
        when(etiquetaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LiberacaoEtiquetaEntity etiqueta(String nome) {
        LiberacaoEtiquetaEntity etiqueta = LiberacaoEtiquetaEntity.builder().id(UUID.randomUUID()).nome(nome)
                .cor(CorLiberacao.VERMELHO).criadoEm(LocalDateTime.now()).build();
        when(etiquetaRepository.findById(etiqueta.getId())).thenReturn(Optional.of(etiqueta));
        return etiqueta;
    }

    @Test
    @DisplayName("criarEtiqueta: nome repetido (sem diferenciar maiúscula) devolve a existente")
    void shouldReuseExistingLabel() {
        LiberacaoEtiquetaEntity urgente = etiqueta("Urgente");
        when(etiquetaRepository.findByNomeIgnoreCase("urgente")).thenReturn(Optional.of(urgente));
        assertThat(service.criarEtiqueta("  urgente ", CorLiberacao.AZUL, aline)).isSameAs(urgente);
        verify(etiquetaRepository, never()).save(any());
    }

    @Test
    @DisplayName("criarEtiqueta: qualquer usuário cria; sem cor vira cinza; espaços colapsam")
    void shouldCreateLabel() {
        when(etiquetaRepository.findByNomeIgnoreCase(anyString())).thenReturn(Optional.empty());
        LiberacaoEtiquetaEntity criada = service.criarEtiqueta("Cliente   novo", null, aline);
        assertThat(criada.getNome()).isEqualTo("Cliente novo");
        assertThat(criada.getCor()).isEqualTo(CorLiberacao.CINZA);
        assertThat(criada.getCriadoPorNome()).isEqualTo("Aline");
    }

    @Test
    @DisplayName("criarEtiqueta: nome vazio ou acima de 40 caracteres é recusado")
    void shouldValidateLabelName() {
        assertThatThrownBy(() -> service.criarEtiqueta("  ", null, aline)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.criarEtiqueta("x".repeat(41), null, aline)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("editar e apagar etiqueta: só analista")
    void shouldRestrictLabelAdmin() {
        LiberacaoEtiquetaEntity urgente = etiqueta("Urgente");
        assertThatThrownBy(() -> service.editarEtiqueta(urgente.getId(), "X", null, aline)).isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.apagarEtiqueta(urgente.getId(), aline)).isInstanceOf(AcessoNegadoException.class);
        when(etiquetaRepository.findByNomeIgnoreCase("Prioridade")).thenReturn(Optional.empty());
        assertThat(service.editarEtiqueta(urgente.getId(), "Prioridade", CorLiberacao.ROXO, andressa).getCor()).isEqualTo(CorLiberacao.ROXO);
    }

    @Test
    @DisplayName("definirEtiquetas: troca o conjunto e registra antes e depois no histórico")
    void shouldReplaceCardLabels() {
        LiberacaoEtiquetaEntity urgente = etiqueta("Urgente");
        LiberacaoEtiquetaEntity novo = etiqueta("Cliente novo");
        when(cardEtiquetaRepository.findByCardId(card.getId())).thenReturn(List.of(new LiberacaoCardEtiquetaEntity(card.getId(), urgente.getId())));
        when(etiquetaRepository.findAllById(any())).thenAnswer(inv -> {
            Iterable<UUID> ids = inv.getArgument(0);
            List<LiberacaoEtiquetaEntity> todas = List.of(urgente, novo);
            return todas.stream().filter(e -> java.util.stream.StreamSupport.stream(ids.spliterator(), false).anyMatch(e.getId()::equals)).toList();
        });

        service.definirEtiquetas(card.getId(), List.of(novo.getId()), aline);

        verify(cardEtiquetaRepository).apagarDoCard(card.getId());
        verify(liberacaoService).registrarEdicao(card, "etiquetas", "Urgente", "Cliente novo", aline);
        verify(cardRepository).tocarSemVersao(eq(card.getId()), any(), eq(aline.getId()), eq("Aline"));
    }

    @Test
    @DisplayName("definirCor: auxiliar não muda cor de card no Comitê; analista muda, sem passar pela versão")
    void shouldGuardColor() {
        card.setEtapa(EtapaLiberacao.COMITE);
        assertThatThrownBy(() -> service.definirCor(card.getId(), CorLiberacao.VERDE, aline)).isInstanceOf(AcessoNegadoException.class);
        service.definirCor(card.getId(), CorLiberacao.VERDE, andressa);
        verify(cardRepository).definirCor(card.getId(), "VERDE");
        verify(liberacaoService).registrarEdicao(card, "cor", "sem cor", "verde", andressa);
    }

    @Test
    @DisplayName("membros: qualquer um segue ou deixa de seguir a si mesmo; outra pessoa no Comitê só analista")
    void shouldGuardMembers() {
        card.setEtapa(EtapaLiberacao.COMITE);
        when(userRepository.existsById(any())).thenReturn(true);

        service.adicionarMembro(card.getId(), aline.getId(), aline);
        verify(liberacaoService).acompanhar(card.getId(), aline.getId(), OrigemMembro.MANUAL);

        UUID outra = UUID.randomUUID();
        assertThatThrownBy(() -> service.adicionarMembro(card.getId(), outra, aline)).isInstanceOf(AcessoNegadoException.class);
        service.adicionarMembro(card.getId(), outra, andressa);
        service.removerMembro(card.getId(), aline.getId(), aline);
        verify(membroRepository).deleteById(any());
    }
}
