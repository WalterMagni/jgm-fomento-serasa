package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.email.RedefinicaoSenhaEmail;
import com.portal.serasa.infrastructure.persistence.entity.RedefinicaoSenhaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.RedefinicaoSenhaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** Redefinição de senha: link de uso único, 30 minutos, código só no e-mail, limite de pedidos. */
@ExtendWith(MockitoExtension.class)
class RedefinicaoSenhaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-12T15:00:00Z");
    private static final UUID ID = UUID.randomUUID();

    @Mock private UserRepository usuarios;
    @Mock private RedefinicaoSenhaRepository pedidos;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedefinicaoSenhaEmail email;

    private RedefinicaoSenhaService service;
    private UserEntity usuario;

    @BeforeEach
    void setUp() {
        service = new RedefinicaoSenhaService(usuarios, pedidos, passwordEncoder, email, Clock.fixed(AGORA, ZoneOffset.UTC));
        usuario = UserEntity.builder().id(ID).name("Maria Silva").email("maria@jgm.com.br").passwordHash("antigo").role("ROLE_USER").build();
    }

    private RedefinicaoSenhaEntity pedido(String codigo, Instant expira, Instant usado) {
        return RedefinicaoSenhaEntity.builder().userId(ID).tokenHash(RedefinicaoSenhaService.hash(codigo))
                .criadoEm(AGORA.minusSeconds(60)).expiraEm(expira).usadoEm(usado).build();
    }

    @Test
    @DisplayName("pedido grava só o hash do código, vale 30 minutos, cancela os anteriores e manda o código por e-mail")
    void solicitarGravaHashEEnviaCodigo() {
        when(usuarios.findByEmail("maria@jgm.com.br")).thenReturn(Optional.of(usuario));
        when(pedidos.countByUserIdAndCriadoEmAfter(eq(ID), any())).thenReturn(0L);

        service.solicitar("  maria@jgm.com.br ");

        ArgumentCaptor<RedefinicaoSenhaEntity> salvo = ArgumentCaptor.forClass(RedefinicaoSenhaEntity.class);
        ArgumentCaptor<String> codigo = ArgumentCaptor.forClass(String.class);
        verify(pedidos).encerrarAbertos(ID, AGORA);
        verify(pedidos).save(salvo.capture());
        verify(email).enviarLink(eq("maria@jgm.com.br"), eq("Maria Silva"), codigo.capture());
        assertThat(codigo.getValue()).hasSize(43);
        assertThat(salvo.getValue().getTokenHash())
                .isEqualTo(RedefinicaoSenhaService.hash(codigo.getValue()))
                .isNotEqualTo(codigo.getValue());
        assertThat(salvo.getValue().getExpiraEm()).isEqualTo(AGORA.plusSeconds(30 * 60));
    }

    @Test
    @DisplayName("e-mail desconhecido não grava nem envia nada")
    void solicitarEmailDesconhecido() {
        when(usuarios.findByEmail("ninguem@jgm.com.br")).thenReturn(Optional.empty());

        service.solicitar("ninguem@jgm.com.br");

        verify(pedidos, never()).save(any());
        verify(email, never()).enviarLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a partir de 3 pedidos em 15 minutos, os seguintes são ignorados")
    void solicitarLimitaPedidos() {
        when(usuarios.findByEmail("maria@jgm.com.br")).thenReturn(Optional.of(usuario));
        when(pedidos.countByUserIdAndCriadoEmAfter(ID, AGORA.minusSeconds(15 * 60))).thenReturn(3L);

        service.solicitar("maria@jgm.com.br");

        verify(pedidos, never()).save(any());
        verify(email, never()).enviarLink(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("link válido troca a senha, marca a troca (derruba sessões), encerra os links e avisa")
    void redefinirComLinkValido() {
        when(pedidos.findByTokenHash(RedefinicaoSenhaService.hash("codigo-bom")))
                .thenReturn(Optional.of(pedido("codigo-bom", AGORA.plusSeconds(600), null)));
        when(usuarios.findById(ID)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("nova-senha-123")).thenReturn("hash-novo");

        service.redefinir("codigo-bom", "nova-senha-123");

        assertThat(usuario.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(usuario.getSenhaAlteradaEm()).isEqualTo(AGORA);
        verify(usuarios).save(usuario);
        verify(pedidos).encerrarAbertos(ID, AGORA);
        verify(email).avisarTroca("maria@jgm.com.br", "Maria Silva");
    }

    @Test
    @DisplayName("link vencido ou já usado é recusado e a senha não muda")
    void redefinirComLinkVencidoOuUsado() {
        when(pedidos.findByTokenHash(RedefinicaoSenhaService.hash("vencido")))
                .thenReturn(Optional.of(pedido("vencido", AGORA.minusSeconds(1), null)));
        when(pedidos.findByTokenHash(RedefinicaoSenhaService.hash("usado")))
                .thenReturn(Optional.of(pedido("usado", AGORA.plusSeconds(600), AGORA.minusSeconds(30))));

        assertThatThrownBy(() -> service.redefinir("vencido", "nova-senha-123"))
                .isInstanceOf(RedefinicaoSenhaService.LinkInvalidoException.class);
        assertThatThrownBy(() -> service.redefinir("usado", "nova-senha-123"))
                .isInstanceOf(RedefinicaoSenhaService.LinkInvalidoException.class);
        assertThat(service.linkValido("vencido")).isFalse();
        verify(usuarios, never()).save(any());
    }

    @Test
    @DisplayName("senha curta ou longa demais é recusada antes de tocar no link")
    void redefinirValidaSenha() {
        assertThatThrownBy(() -> service.redefinir("qualquer", "1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("8");
        assertThatThrownBy(() -> service.redefinir("qualquer", "x".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(pedidos, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("códigos novos não se repetem")
    void codigosAleatorios() {
        assertThat(RedefinicaoSenhaService.novoCodigo()).isNotEqualTo(RedefinicaoSenhaService.novoCodigo());
    }
}
