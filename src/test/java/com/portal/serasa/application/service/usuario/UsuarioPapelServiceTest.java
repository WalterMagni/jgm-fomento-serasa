package com.portal.serasa.application.service.usuario;

import com.portal.serasa.domain.exception.EntityNotFoundException;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
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
class UsuarioPapelServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private LiberacaoParecerJpaRepository parecerRepository;

    @InjectMocks private UsuarioPapelService service;

    private UserEntity admin;
    private UserEntity alvo;

    @BeforeEach
    void setUp() {
        admin = UserEntity.builder().id(UUID.randomUUID()).name("Walter").email("walter@jgm.com").build();
        alvo = UserEntity.builder().id(UUID.randomUUID()).name("Mychelly").email("mychelly@jgm.com")
                .analista(true).comite(true).build();
        when(userRepository.findById(alvo.getId())).thenReturn(Optional.of(alvo));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("definir: Comitê sem analista é IllegalArgument e nada é gravado")
    void shouldRejectComiteWithoutAnalista() {
        assertThatThrownBy(() -> service.definir(alvo.getId(), false, true, false, false, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Membro do Comitê precisa ser analista.");
        verify(userRepository, never()).save(any());
        verify(parecerRepository, never()).apagarAguardandoDoUsuario(any());
    }

    @Test
    @DisplayName("definir: usuário inexistente é EntityNotFound")
    void shouldFailWhenUserDoesNotExist() {
        UUID fantasma = UUID.randomUUID();
        when(userRepository.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.definir(fantasma, true, false, false, false, admin))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("definir: sair do Comitê apaga os pareceres que o usuário ainda devia")
    void shouldClearPendingPareceresWhenLeavingComite() {
        UserEntity salvo = service.definir(alvo.getId(), true, false, false, false, admin);

        assertThat(salvo.isAnalista()).isTrue();
        assertThat(salvo.isComite()).isFalse();
        verify(userRepository).save(alvo);
        verify(parecerRepository).apagarAguardandoDoUsuario(alvo.getId());
    }

    @Test
    @DisplayName("definir: deixar de ser analista (e portanto do Comitê) também apaga os pareceres pendentes")
    void shouldClearPendingPareceresWhenLosingAnalistaAndComite() {
        UserEntity salvo = service.definir(alvo.getId(), false, false, false, false, admin);

        assertThat(salvo.isAnalista()).isFalse();
        assertThat(salvo.isComite()).isFalse();
        verify(parecerRepository).apagarAguardandoDoUsuario(alvo.getId());
    }

    @Test
    @DisplayName("definir: permanecer no Comitê não mexe nos pareceres")
    void shouldKeepPareceresWhenStayingInComite() {
        UserEntity salvo = service.definir(alvo.getId(), true, true, false, false, admin);

        assertThat(salvo.isComite()).isTrue();
        verify(userRepository).save(alvo);
        verify(parecerRepository, never()).apagarAguardandoDoUsuario(any());
    }

    @Test
    @DisplayName("definir: entrar no Comitê não apaga pareceres")
    void shouldNotClearPareceresWhenJoiningComite() {
        alvo.setComite(false);

        UserEntity salvo = service.definir(alvo.getId(), true, true, false, false, admin);

        assertThat(salvo.isComite()).isTrue();
        verify(parecerRepository, never()).apagarAguardandoDoUsuario(any());
    }

    @Test
    @DisplayName("definir: quem nunca foi do Comitê e continua fora não mexe nos pareceres")
    void shouldNotClearPareceresWhenWasNeverInComite() {
        alvo.setComite(false);

        service.definir(alvo.getId(), false, false, false, false, admin);

        verify(parecerRepository, never()).apagarAguardandoDoUsuario(any());
    }

    @Test
    @DisplayName("definir: Diretoria sem analista é IllegalArgument e nada é gravado")
    void shouldRejectDiretoriaWithoutAnalista() {
        assertThatThrownBy(() -> service.definir(alvo.getId(), false, false, true, false, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diretoria precisa ser analista.");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("definir: Diretoria acumula com Comitê")
    void shouldAllowDiretoriaAndComiteTogether() {
        UserEntity salvo = service.definir(alvo.getId(), true, true, true, false, admin);

        assertThat(salvo.isComite()).isTrue();
        assertThat(salvo.isDiretoria()).isTrue();
        verify(parecerRepository, never()).apagarAguardandoDoUsuario(any());
    }

    @Test
    @DisplayName("definir: marca a Diretoria e a devolve ligada, sem mexer nos pareceres de quem continua no Comitê")
    void shouldSaveDiretoriaMark() {
        UserEntity salvo = service.definir(alvo.getId(), true, true, true, false, admin);

        assertThat(salvo.isDiretoria()).isTrue();
        verify(userRepository).save(alvo);
    }

    @Test
    @DisplayName("definir: tirar a marca de Diretoria a desliga e não apaga parecer nenhum (Diretoria não deve parecer)")
    void shouldClearDiretoriaMarkWithoutTouchingPareceres() {
        alvo.setDiretoria(true);

        UserEntity salvo = service.definir(alvo.getId(), true, true, false, false, admin);

        assertThat(salvo.isDiretoria()).isFalse();
        assertThat(salvo.isComite()).isTrue();
        verify(parecerRepository, never()).apagarAguardandoDoUsuario(any());
    }

    @Test
    @DisplayName("definir: deixar de ser analista com a Diretoria ligada é recusado — a Diretoria exige analista — e nada é gravado")
    void shouldRejectLosingAnalistaWhileStillDiretoria() {
        alvo.setDiretoria(true);

        assertThatThrownBy(() -> service.definir(alvo.getId(), false, false, true, false, admin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Diretoria precisa ser analista.");
        assertThat(alvo.isAnalista()).isTrue();
        assertThat(alvo.isDiretoria()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("definir: tirar analista e Diretoria juntos funciona e também apaga os pareceres pendentes do Comitê")
    void shouldLoseAnalistaAndDiretoriaTogether() {
        alvo.setDiretoria(true);

        UserEntity salvo = service.definir(alvo.getId(), false, false, false, false, admin);

        assertThat(salvo.isAnalista()).isFalse();
        assertThat(salvo.isComite()).isFalse();
        assertThat(salvo.isDiretoria()).isFalse();
        verify(parecerRepository).apagarAguardandoDoUsuario(alvo.getId());
    }

    @Test
    @DisplayName("definir: Judit sem analista é IllegalArgument e nada é gravado")
    void juditSemAnalista() {
        assertThatThrownBy(() -> service.definir(alvo.getId(), false, false, false, true, admin))
                .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }
}
