package com.portal.serasa.application.service.notificacao;

import com.portal.serasa.application.service.notificacao.NotificacaoService.Nova;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.persistence.entity.NotificacaoEntity;
import com.portal.serasa.infrastructure.persistence.repository.NotificacaoJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificacaoServiceTest {

    @Mock private NotificacaoJpaRepository repository;
    @Mock private SseHub hub;
    @InjectMocks private NotificacaoService service;

    private final UUID ator = UUID.randomUUID();
    private final UUID aline = UUID.randomUUID();
    private final UUID mychelly = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(inv -> {
            NotificacaoEntity entidade = inv.getArgument(0);
            entidade.setId(UUID.randomUUID());
            return entidade;
        });
        when(repository.countByDestinatarioIdAndLidaEmIsNull(any())).thenReturn(3L);
    }

    private Nova nova(UUID para, TipoNotificacao tipo) {
        return new Nova(para, tipo, "título", "resumo", "/liberacao?card=1");
    }

    @Test
    @DisplayName("notificar: quem agiu não recebe, e cada pessoa recebe só a primeira da lista")
    void shouldSkipActorAndDeduplicate() {
        var entregas = service.notificar(List.of(
                nova(aline, TipoNotificacao.MENCAO),
                nova(aline, TipoNotificacao.COMENTARIO),
                nova(ator, TipoNotificacao.COMENTARIO),
                nova(mychelly, TipoNotificacao.COMENTARIO)), ator, "Andressa");

        ArgumentCaptor<NotificacaoEntity> salvas = ArgumentCaptor.forClass(NotificacaoEntity.class);
        verify(repository, times(2)).save(salvas.capture());
        assertThat(salvas.getAllValues()).extracting(NotificacaoEntity::getDestinatarioId).containsExactly(aline, mychelly);
        assertThat(salvas.getAllValues().get(0).getTipo()).isEqualTo(TipoNotificacao.MENCAO);
        assertThat(salvas.getAllValues().get(0).getAtorNome()).isEqualTo("Andressa");
        assertThat(entregas).allMatch(entrega -> entrega.naoLidas() == 3L);
    }

    @Test
    @DisplayName("notificar: resumo longo é cortado com reticências")
    void shouldTruncateLongSummary() {
        service.notificar(List.of(new Nova(aline, TipoNotificacao.COMENTARIO, "t", "x".repeat(900), "/l")), ator, "A");
        ArgumentCaptor<NotificacaoEntity> salva = ArgumentCaptor.forClass(NotificacaoEntity.class);
        verify(repository).save(salva.capture());
        assertThat(salva.getValue().getResumo()).hasSize(500).endsWith("…");
    }

    @Test
    @DisplayName("entregar: manda pelo canal de cada destinatário com a contagem")
    void shouldPushToEachRecipient() {
        var entregas = service.notificar(List.of(nova(aline, TipoNotificacao.MENCAO)), ator, "A");
        service.entregar(entregas);
        verify(hub).enviar(eq(aline), eq(NotificacaoService.EVENTO_NOTIFICACAO), anyMap());
    }

    @Test
    @DisplayName("marcarLida: notificação de outra pessoa não é encontrada")
    void shouldNotMarkOthersNotification() {
        NotificacaoEntity alheia = NotificacaoEntity.builder().id(UUID.randomUUID()).destinatarioId(mychelly).build();
        when(repository.findById(alheia.getId())).thenReturn(Optional.of(alheia));
        assertThatThrownBy(() -> service.marcarLida(alheia.getId(), aline)).isInstanceOf(EntityNotFoundException.class);
    }
}
