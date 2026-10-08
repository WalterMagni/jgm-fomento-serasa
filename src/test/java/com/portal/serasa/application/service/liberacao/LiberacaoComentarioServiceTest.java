package com.portal.serasa.application.service.liberacao;

import com.portal.serasa.domain.exception.AcessoNegadoException;
import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.domain.model.liberacao.EtapaLiberacao;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoCardEntity;
import com.portal.serasa.infrastructure.persistence.entity.LiberacaoComentarioEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.LiberacaoComentarioJpaRepository;
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
class LiberacaoComentarioServiceTest {

    @Mock private LiberacaoComentarioJpaRepository comentarioRepository;
    @Mock private LiberacaoService liberacaoService;
    @Spy private LiberacaoAutorizacao autorizacao = new LiberacaoAutorizacao();

    @InjectMocks private LiberacaoComentarioService service;

    private UserEntity aline;
    private UserEntity andressa;
    private LiberacaoCardEntity card;

    @BeforeEach
    void setUp() {
        aline = UserEntity.builder().id(UUID.randomUUID()).name("Aline").build();
        andressa = UserEntity.builder().id(UUID.randomUUID()).name("Andressa").analista(true).comite(true).build();
        card = LiberacaoCardEntity.builder().id(UUID.randomUUID()).etapa(EtapaLiberacao.COMITE).build();
        when(liberacaoService.buscar(card.getId())).thenReturn(card);
        when(comentarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LiberacaoComentarioEntity comentarioDe(UserEntity autor) {
        LiberacaoComentarioEntity comentario = LiberacaoComentarioEntity.builder()
                .id(UUID.randomUUID()).cardId(card.getId()).autorId(autor.getId()).autorNome(autor.getName())
                .texto("original").criadoEm(LocalDateTime.now()).build();
        when(comentarioRepository.findById(comentario.getId())).thenReturn(Optional.of(comentario));
        return comentario;
    }

    @Test
    @DisplayName("comentar: auxiliar comenta em card no Comitê, texto aparado")
    void shouldLetAnyoneComment() {
        LiberacaoComentarioEntity salvo = service.comentar(card.getId(), "  confirmado com o sacado  ", aline);
        assertThat(salvo.getTexto()).isEqualTo("confirmado com o sacado");
        assertThat(salvo.getAutorNome()).isEqualTo("Aline");
        assertThat(salvo.getCardId()).isEqualTo(card.getId());
    }

    @Test
    @DisplayName("comentar: texto vazio é recusado")
    void shouldRejectBlank() {
        assertThatThrownBy(() -> service.comentar(card.getId(), "   ", aline)).isInstanceOf(IllegalArgumentException.class);
        verify(comentarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar: autor edita, marca editado_em e devolve o texto anterior")
    void shouldLetAuthorEdit() {
        LiberacaoComentarioEntity comentario = comentarioDe(aline);
        var edicao = service.editar(card.getId(), comentario.getId(), "novo", aline);
        assertThat(edicao.textoAnterior()).isEqualTo("original");
        assertThat(edicao.comentario().getTexto()).isEqualTo("novo");
        assertThat(edicao.comentario().getEditadoEm()).isNotNull();
    }

    @Test
    @DisplayName("editar: mesmo texto não marca como editado")
    void shouldNotMarkEditedWhenUnchanged() {
        LiberacaoComentarioEntity comentario = comentarioDe(aline);
        service.editar(card.getId(), comentario.getId(), " original ", aline);
        assertThat(comentario.getEditadoEm()).isNull();
        verify(comentarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("editar e apagar: nem analista mexe em comentário alheio")
    void shouldBlockOthers() {
        LiberacaoComentarioEntity comentario = comentarioDe(aline);
        assertThatThrownBy(() -> service.editar(card.getId(), comentario.getId(), "x", andressa))
                .isInstanceOf(AcessoNegadoException.class);
        assertThatThrownBy(() -> service.apagar(card.getId(), comentario.getId(), andressa))
                .isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    @DisplayName("apagar: esconde com excluido_em; apagado não é editável")
    void shouldSoftDelete() {
        LiberacaoComentarioEntity comentario = comentarioDe(aline);
        service.apagar(card.getId(), comentario.getId(), aline);
        assertThat(comentario.getExcluidoEm()).isNotNull();
        assertThatThrownBy(() -> service.editar(card.getId(), comentario.getId(), "x", aline))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("comentário de outro card não é encontrado pelo caminho deste")
    void shouldNotCrossCards() {
        LiberacaoComentarioEntity comentario = comentarioDe(aline);
        comentario.setCardId(UUID.randomUUID());
        assertThatThrownBy(() -> service.apagar(card.getId(), comentario.getId(), aline))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
