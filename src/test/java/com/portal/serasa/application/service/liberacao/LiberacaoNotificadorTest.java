package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Nova;
import com.portal.serasa.application.service.notificacao.SseHub;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.domain.model.liberacao.OrigemMembro;
import com.portal.serasa.domain.model.liberacao.PosicaoParecer;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoMembroEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoParecerEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoMembroJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoParecerJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiberacaoNotificadorTest {

    @Mock private NotificacaoService notificacaoService;
    @Mock private SseHub hub;
    @Mock private LiberacaoMembroJpaRepository membroRepository;
    @Mock private LiberacaoParecerJpaRepository parecerRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private LiberacaoNotificador notificador;

    private UserEntity aline;
    private UserEntity andressa;
    private UserEntity mychelly;
    private UserEntity diretoria;
    private LiberacaoCardEntity card;

    @BeforeEach
    void setUp() {
        aline = usuario("Aline Souza", false, false);
        andressa = usuario("Andressa Lima", true, true);
        mychelly = usuario("Mychelly Costa", true, true);
        diretoria = usuario("Diretoria", true, false);
        card = LiberacaoCardEntity.builder().id(UUID.randomUUID()).numero(42L).etapa(EtapaLiberacao.COMITE).rodada(1)
                .cedenteNome("ACME LTDA").valor(new BigDecimal("80000")).criadoPorId(aline.getId()).build();
        when(userRepository.findByAnalistaTrue()).thenReturn(List.of(andressa, mychelly, diretoria));
        when(parecerRepository.findByCardIdAndRodadaOrderByCriadoEm(card.getId(), 1)).thenReturn(List.of(
                parecer(andressa, PosicaoParecer.FAVORAVEL), parecer(mychelly, null)));
        when(membroRepository.findByCardIdOrderByAdicionadoEm(card.getId())).thenReturn(List.of(
                membro(aline), membro(andressa), membro(mychelly)));
    }

    private UserEntity usuario(String nome, boolean analista, boolean comite) {
        return UserEntity.builder().id(UUID.randomUUID()).name(nome).analista(analista).comite(comite).build();
    }

    private LiberacaoParecerEntity parecer(UserEntity user, PosicaoParecer posicao) {
        return LiberacaoParecerEntity.builder().cardId(card.getId()).rodada(1).usuarioId(user.getId())
                .usuarioNome(user.getName()).posicao(posicao).build();
    }

    private LiberacaoMembroEntity membro(UserEntity user) {
        return LiberacaoMembroEntity.builder().cardId(card.getId()).usuarioId(user.getId())
                .origem(OrigemMembro.MANUAL).adicionadoEm(LocalDateTime.now()).build();
    }

    private Map<UUID, TipoNotificacao> porPessoa(List<Nova> novas) {
        return novas.stream().collect(java.util.stream.Collectors.toMap(Nova::destinatarioId, Nova::tipo, (a, b) -> a));
    }

    @Test
    @DisplayName("card criado: avisa todas as analistas, Diretoria inclusive")
    void shouldNotifyAnalystsOnCreate() {
        card.setEtapa(EtapaLiberacao.ORIGEM);
        List<Nova> novas = notificador.destinatarios(new LiberacaoEvento.Criado(card, aline, Set.of(), null));
        assertThat(porPessoa(novas)).containsOnlyKeys(andressa.getId(), mychelly.getId(), diretoria.getId())
                .containsValue(TipoNotificacao.CARD_CRIADO);
        assertThat(novas.get(0).titulo()).isEqualTo("Aline abriu #42");
        assertThat(novas.get(0).link()).isEqualTo("/liberacao?card=" + card.getId());
    }

    @Test
    @DisplayName("entrou no Comitê: o Comitê da rodada recebe 'seu parecer é esperado'")
    void shouldAskComiteForParecer() {
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.ORIGEM, EtapaLiberacao.COMITE, aline, Set.of(), null));
        assertThat(porPessoa(novas)).containsOnlyKeys(andressa.getId(), mychelly.getId());
        assertThat(novas).allMatch(nova -> nova.tipo() == TipoNotificacao.PARECER_ESPERADO);
    }

    @Test
    @DisplayName("aprovado: criador e membros recebem a decisão")
    void shouldNotifyDecision() {
        card.setEtapa(EtapaLiberacao.APROVADO);
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Movido(card, EtapaLiberacao.COMITE, EtapaLiberacao.APROVADO, andressa, Set.of(), null));
        assertThat(porPessoa(novas)).containsKeys(aline.getId(), mychelly.getId());
        assertThat(novas.get(0).titulo()).isEqualTo("#42 aprovado por Andressa");
        assertThat(novas).allMatch(nova -> nova.tipo() == TipoNotificacao.DECISAO);
    }

    @Test
    @DisplayName("último parecer: Comitê completo; parecer intermediário: parecer registrado")
    void shouldDistinguishLastParecer() {
        LiberacaoParecerEntity dado = parecer(mychelly, PosicaoParecer.COM_RESSALVAS);
        assertThat(notificador.destinatarios(new LiberacaoEvento.ParecerDado(card, dado, true, false, mychelly, Set.of(), null)))
                .allMatch(nova -> nova.tipo() == TipoNotificacao.COMITE_COMPLETO);
        assertThat(notificador.destinatarios(new LiberacaoEvento.ParecerDado(card, dado, false, false, mychelly, Set.of(), null)))
                .allMatch(nova -> nova.tipo() == TipoNotificacao.PARECER_REGISTRADO);
    }

    @Test
    @DisplayName("pendência aberta avisa o destinatário; respondida avisa quem abriu")
    void shouldRoutePendencia() {
        LiberacaoPendenciaEntity pendencia = LiberacaoPendenciaEntity.builder()
                .abertaPorId(andressa.getId()).destinatarioId(aline.getId()).texto("Confirmar aceite").build();
        assertThat(porPessoa(notificador.destinatarios(new LiberacaoEvento.PendenciaAberta(card, pendencia, andressa, Set.of(), "Confirmar aceite"))))
                .containsExactly(Map.entry(aline.getId(), TipoNotificacao.PENDENCIA_ABERTA));
        assertThat(porPessoa(notificador.destinatarios(new LiberacaoEvento.PendenciaRespondida(card, pendencia, aline, Set.of(), "Ok"))))
                .containsExactly(Map.entry(andressa.getId(), TipoNotificacao.PENDENCIA_RESPONDIDA));
    }

    @Test
    @DisplayName("comentário com menção: marcado recebe MENCAO antes do aviso de comentário")
    void shouldPutMentionFirst() {
        LiberacaoComentarioEntity comentario = LiberacaoComentarioEntity.builder().texto("x").build();
        List<Nova> novas = notificador.destinatarios(
                new LiberacaoEvento.Comentado(card, comentario, false, andressa, Set.of(aline.getId()), "Aline, confere?"));
        assertThat(novas.get(0)).satisfies(nova -> {
            assertThat(nova.destinatarioId()).isEqualTo(aline.getId());
            assertThat(nova.tipo()).isEqualTo(TipoNotificacao.MENCAO);
            assertThat(nova.titulo()).isEqualTo("Andressa te marcou em #42");
            assertThat(nova.resumo()).isEqualTo("ACME LTDA · Aline, confere?");
        });
        assertThat(novas).anyMatch(nova -> nova.destinatarioId().equals(mychelly.getId()) && nova.tipo() == TipoNotificacao.COMENTARIO);
    }

    @Test
    @DisplayName("comentário editado só avisa quem passou a ser marcado")
    void shouldNotNotifyMembersOnEdit() {
        LiberacaoComentarioEntity comentario = LiberacaoComentarioEntity.builder().texto("x").build();
        assertThat(notificador.destinatarios(new LiberacaoEvento.Comentado(card, comentario, true, andressa, Set.of(), null))).isEmpty();
    }

    @Test
    @DisplayName("falha ao notificar não impede o aviso de quadro alterado")
    void shouldBroadcastBoardEvenWhenNotificationFails() {
        doThrow(new RuntimeException("banco fora")).when(notificacaoService).notificar(anyCollection(), any(), any());
        notificador.aoAcontecer(new LiberacaoEvento.Criado(card, aline, Set.of(), null));
        verify(hub).enviarTodos(eq(LiberacaoNotificador.EVENTO_QUADRO), any());
    }
}
