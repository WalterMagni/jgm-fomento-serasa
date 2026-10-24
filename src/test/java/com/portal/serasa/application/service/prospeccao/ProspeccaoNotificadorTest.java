package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.application.service.notificacao.NotificacaoService;
import com.portal.serasa.application.service.notificacao.NotificacaoService.Nova;
import com.portal.serasa.application.service.notificacao.SseHub;
import com.portal.serasa.domain.model.notificacao.TipoNotificacao;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.RespostaCliente;
import com.portal.serasa.infrastructure.email.LiberacaoEmail;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoComentarioJpaRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoNotificadorTest {

    @Mock private NotificacaoService notificacaoService;
    @Mock private SseHub hub;
    @Mock private UserRepository userRepository;
    @Mock private ProspeccaoComentarioJpaRepository comentarioRepository;
    @Mock private LiberacaoEmail email;

    @InjectMocks private ProspeccaoNotificador notificador;

    private UserEntity dona;
    private UserEntity autor;
    private ProspeccaoEntity card;

    @BeforeEach
    void setUp() {
        dona = UserEntity.builder().id(UUID.randomUUID()).name("Andressa Lima").build();
        autor = UserEntity.builder().id(UUID.randomUUID()).name("Mychelly Souza").build();
        card = ProspeccaoEntity.builder().id(UUID.randomUUID()).razaoSocial("ACME LTDA")
                .estagio(EstagioProspeccao.EM_ANALISE).analistaId(dona.getId()).build();
    }

    @Test
    @DisplayName("card criado à mão avisa todas as analistas")
    void shouldNotifyAnalystsOnCreation() {
        UserEntity a1 = UserEntity.builder().id(UUID.randomUUID()).build();
        UserEntity a2 = UserEntity.builder().id(UUID.randomUUID()).build();
        when(userRepository.findByAnalistaTrue()).thenReturn(List.of(a1, a2));

        List<Nova> novas = notificador.destinatarios(new ProspeccaoAcontecimento.Criado(card, autor));

        assertThat(novas).extracting(Nova::destinatarioId).containsExactly(a1.getId(), a2.getId());
        assertThat(novas).allSatisfy(n -> assertThat(n.tipo()).isEqualTo(TipoNotificacao.CARD_CRIADO));
        assertThat(novas.get(0).titulo()).contains("Mychelly").contains("ACME LTDA");
        assertThat(novas.get(0).link()).isEqualTo("/prospeccao?card=" + card.getId());
    }

    @Test
    @DisplayName("pendência respondida avisa quem abriu")
    void shouldNotifyOpenerOnAnswer() {
        ProspeccaoPendenciaEntity pendencia = ProspeccaoPendenciaEntity.builder().abertaPor(dona.getId()).build();

        List<Nova> novas = notificador.destinatarios(
                new ProspeccaoAcontecimento.PendenciaRespondida(card, pendencia, autor, "enviado"));

        assertThat(novas).singleElement().satisfies(n -> {
            assertThat(n.destinatarioId()).isEqualTo(dona.getId());
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.PENDENCIA_RESPONDIDA);
        });
    }

    @Test
    @DisplayName("resposta do cliente avisa a dona do card")
    void shouldNotifyOwnerOnClientAnswer() {
        List<Nova> novas = notificador.destinatarios(
                new ProspeccaoAcontecimento.RespostaCliente(card, RespostaCliente.CONTRAPROPOSTA, autor, "quer mais"));

        assertThat(novas).singleElement().satisfies(n -> {
            assertThat(n.destinatarioId()).isEqualTo(dona.getId());
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.RESPOSTA_CLIENTE);
            assertThat(n.titulo()).contains("contraproposta");
        });
    }

    @Test
    @DisplayName("cancelado depois da aprovação avisa o comercial, a dona e os membros do comitê da rodada, cada um uma vez")
    void shouldNotifyEveryoneWaitingOnCancellation() {
        UUID comercial = UUID.randomUUID();
        UUID membro = UUID.randomUUID();
        card.setComercialId(comercial);

        List<Nova> novas = notificador.destinatarios(new ProspeccaoAcontecimento.Cancelado(card, EstagioProspeccao.COMITE,
                List.of(membro, dona.getId()), autor, "Reprovado depois da aprovação, em Comitê — Segmento"));

        assertThat(novas).extracting(Nova::destinatarioId).containsExactly(comercial, dona.getId(), membro);
        assertThat(novas).allSatisfy(n -> {
            assertThat(n.tipo()).isEqualTo(TipoNotificacao.DECISAO);
            assertThat(n.titulo()).isEqualTo("ACME LTDA reprovada depois da aprovação");
            assertThat(n.resumo()).contains("Segmento");
        });
    }

    @Test
    @DisplayName("comentário: menção vem antes, e avisa a dona e quem já comentou")
    void shouldPutMentionFirstOnComment() {
        UUID marcada = UUID.randomUUID();
        UUID jaComentou = UUID.randomUUID();
        when(comentarioRepository.findByProspeccaoIdAndExcluidoEmIsNullOrderByCriadoEmDesc(card.getId()))
                .thenReturn(List.of(ProspeccaoComentarioEntity.builder().autorId(jaComentou).build()));

        List<Nova> novas = notificador.destinatarios(
                new ProspeccaoAcontecimento.Comentado(card, false, autor, Set.of(marcada), "olha isso"));

        assertThat(novas.get(0).tipo()).isEqualTo(TipoNotificacao.MENCAO);
        assertThat(novas.get(0).destinatarioId()).isEqualTo(marcada);
        assertThat(novas).extracting(Nova::destinatarioId).contains(dona.getId(), jaComentou);
    }

    @Test
    @DisplayName("edição de comentário só avisa quem passou a ser mencionado")
    void shouldOnlyNotifyNewMentionsOnEdit() {
        List<Nova> novas = notificador.destinatarios(
                new ProspeccaoAcontecimento.Comentado(card, true, autor, Set.of(), "editado"));

        assertThat(novas).isEmpty();
    }

    @Test
    @DisplayName("alteração sem aviso só atualiza o quadro de quem está olhando")
    void shouldOnlyRefreshBoardOnPlainChange() {
        notificador.aoAcontecer(new ProspeccaoAcontecimento.Alterado(card, autor));

        verify(notificacaoService, never()).notificar(any(), any(), any());
        verify(hub).enviarTodos(eq(ProspeccaoNotificador.EVENTO_QUADRO), eq(Map.of("cardId", card.getId())));
    }
}
