package com.portal.serasa.application.service.prospeccao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.TransicaoInvalidaException;
import com.portal.serasa.domain.model.prospeccao.EstagioProspeccao;
import com.portal.serasa.domain.model.prospeccao.StatusPendencia;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoDocumentoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoEntity;
import com.portal.serasa.infrastructure.persistence.entity.ProspeccaoPendenciaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoDocumentoJpaRepository;
import com.portal.serasa.infrastructure.persistence.repository.ProspeccaoPendenciaJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProspeccaoPendenciaServiceTest {

    @Mock private ProspeccaoService prospeccaoService;
    @Mock private ProspeccaoPendenciaJpaRepository pendenciaRepository;
    @Mock private ProspeccaoDocumentoJpaRepository documentoRepository;
    @Mock private ApplicationEventPublisher acontecimentos;

    private ProspeccaoPendenciaService service;
    private UserEntity dona;
    private UserEntity outra;
    private ProspeccaoEntity card;

    @BeforeEach
    void setUp() {
        service = new ProspeccaoPendenciaService(prospeccaoService, new ProspeccaoAutorizacao(), pendenciaRepository,
                documentoRepository, acontecimentos);
        dona = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").role("ROLE_USER").build();
        outra = UserEntity.builder().id(UUID.randomUUID()).name("Mychelly").role("ROLE_USER").build();
        card = ProspeccaoEntity.builder().id(UUID.randomUUID()).cnpj("11222333000181").razaoSocial("ACME")
                .estagio(EstagioProspeccao.EM_ANALISE).analistaId(dona.getId())
                .estagioDesde(LocalDateTime.now()).prazoEstagioHoras(24).build();
        when(prospeccaoService.buscar(card.getId())).thenReturn(card);
        when(pendenciaRepository.save(any())).thenAnswer(inv -> {
            ProspeccaoPendenciaEntity p = inv.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
    }

    private ProspeccaoPendenciaEntity aberta(UUID abertaPor) {
        ProspeccaoPendenciaEntity pendencia = ProspeccaoPendenciaEntity.builder()
                .id(UUID.randomUUID()).prospeccaoId(card.getId()).pergunta("Mandar faturamento")
                .status(StatusPendencia.ABERTA).abertaPor(abertaPor).abertaEm(LocalDateTime.now()).build();
        when(pendenciaRepository.findById(pendencia.getId())).thenReturn(Optional.of(pendencia));
        return pendencia;
    }

    @Test
    @DisplayName("abrir: pausa o prazo, grava campos e documentos pedidos e publica o aviso")
    void shouldOpenAndPause() {
        UUID docId = UUID.randomUUID();
        when(documentoRepository.findByProspeccaoId(card.getId()))
                .thenReturn(List.of(ProspeccaoDocumentoEntity.builder().id(docId).build()));

        ProspeccaoPendenciaEntity pendencia = service.abrir(card.getId(), "Faltam dados de faturamento",
                List.of("faturamentoMensal", "ticketMedio"), List.of(docId), dona);

        assertThat(pendencia.getStatus()).isEqualTo(StatusPendencia.ABERTA);
        assertThat(pendencia.getCamposExigidos()).containsExactly("faturamentoMensal", "ticketMedio");
        assertThat(pendencia.getDocumentosPedidos()).containsExactly(docId);
        verify(prospeccaoService).pausar(card);
        ArgumentCaptor<Object> evento = ArgumentCaptor.forClass(Object.class);
        verify(acontecimentos).publishEvent(evento.capture());
        assertThat(evento.getValue()).isInstanceOf(ProspeccaoAcontecimento.PendenciaAberta.class);
    }

    @Test
    @DisplayName("abrir: campo desconhecido ou documento de outro card não passam")
    void shouldValidateFieldsAndDocuments() {
        assertThatThrownBy(() -> service.abrir(card.getId(), "x", List.of("salario"), null, dona))
                .hasMessageContaining("desconhecido");
        when(documentoRepository.findByProspeccaoId(card.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.abrir(card.getId(), "x", null, List.of(UUID.randomUUID()), dona))
                .hasMessageContaining("não pertence");
        verify(pendenciaRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir: só a dona; card encerrado não aceita pendência")
    void shouldOnlyLetOwnerOpen() {
        assertThatThrownBy(() -> service.abrir(card.getId(), "x", null, null, outra)).isInstanceOf(AcessoNegadoException.class);
        card.setEstagio(EstagioProspeccao.REPROVADO);
        assertThatThrownBy(() -> service.abrir(card.getId(), "x", null, null, dona)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("responder: qualquer pessoa da equipe responde; a última aberta retoma o prazo")
    void shouldResumeWhenLastIsAnswered() {
        ProspeccaoPendenciaEntity pendencia = aberta(dona.getId());
        when(pendenciaRepository.countByProspeccaoIdAndStatus(card.getId(), StatusPendencia.ABERTA)).thenReturn(0L);

        service.responder(pendencia.getId(), "Faturamento enviado por e-mail", outra);

        assertThat(pendencia.getStatus()).isEqualTo(StatusPendencia.RESPONDIDA);
        assertThat(pendencia.getRespondidaPorNome()).isEqualTo("Mychelly");
        verify(prospeccaoService).retomar(card);
    }

    @Test
    @DisplayName("responder: com outra pendência aberta, o prazo continua parado")
    void shouldKeepPausedWhileOthersOpen() {
        ProspeccaoPendenciaEntity pendencia = aberta(dona.getId());
        when(pendenciaRepository.countByProspeccaoIdAndStatus(card.getId(), StatusPendencia.ABERTA)).thenReturn(1L);

        service.responder(pendencia.getId(), "Parte enviada", outra);

        verify(prospeccaoService, never()).retomar(any());
    }

    @Test
    @DisplayName("responder duas vezes não passa")
    void shouldRejectAnsweringTwice() {
        ProspeccaoPendenciaEntity pendencia = aberta(dona.getId());
        pendencia.setStatus(StatusPendencia.RESPONDIDA);

        assertThatThrownBy(() -> service.responder(pendencia.getId(), "de novo", dona))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    @DisplayName("cancelar: quem abriu ou a dona; outra pessoa não")
    void shouldRestrictCancel() {
        ProspeccaoPendenciaEntity daDona = aberta(dona.getId());
        assertThatThrownBy(() -> service.cancelar(daDona.getId(), outra)).isInstanceOf(AcessoNegadoException.class);

        service.cancelar(daDona.getId(), dona);
        assertThat(daDona.getStatus()).isEqualTo(StatusPendencia.CANCELADA);

        ProspeccaoPendenciaEntity daOutra = aberta(outra.getId());
        service.cancelar(daOutra.getId(), outra);
        assertThat(daOutra.getStatus()).isEqualTo(StatusPendencia.CANCELADA);
    }
}
