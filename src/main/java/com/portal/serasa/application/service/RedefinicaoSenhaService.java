package com.portal.serasa.application.service;

import com.portal.serasa.infrastructure.email.RedefinicaoSenhaEmail;
import com.portal.serasa.infrastructure.persistence.entity.RedefinicaoSenhaEntity;
import com.portal.serasa.infrastructure.persistence.entity.UserEntity;
import com.portal.serasa.infrastructure.persistence.repository.RedefinicaoSenhaRepository;
import com.portal.serasa.infrastructure.persistence.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Redefinição de senha por e-mail.
 *
 * <p>O pedido gera um código aleatório de 256 bits que só vai no link do e-mail; o banco guarda o
 * SHA-256 dele. O link vale 30 minutos e uma vez só; um pedido novo cancela os anteriores. Cada
 * e-mail pode pedir até 3 vezes a cada 15 minutos. A resposta ao pedido é sempre a mesma, exista a
 * conta ou não, para ninguém descobrir quem tem acesso testando e-mails.</p>
 *
 * <p>Trocar a senha encerra os logins abertos (ver SecurityFilter) e avisa a pessoa por e-mail.</p>
 */
@Slf4j
@Service
public class RedefinicaoSenhaService {

    static final Duration VALIDADE = Duration.ofMinutes(30);
    static final Duration JANELA_PEDIDOS = Duration.ofMinutes(15);
    static final int MAXIMO_PEDIDOS = 3;
    static final int SENHA_MINIMA = 8;
    /** O BCrypt só considera os primeiros 72 bytes da senha. */
    static final int SENHA_MAXIMA_BYTES = 72;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UserRepository usuarios;
    private final RedefinicaoSenhaRepository pedidos;
    private final PasswordEncoder passwordEncoder;
    private final RedefinicaoSenhaEmail email;
    private final Clock relogio;

    @Autowired
    public RedefinicaoSenhaService(UserRepository usuarios, RedefinicaoSenhaRepository pedidos,
                                   PasswordEncoder passwordEncoder, RedefinicaoSenhaEmail email) {
        this(usuarios, pedidos, passwordEncoder, email, Clock.systemUTC());
    }

    RedefinicaoSenhaService(UserRepository usuarios, RedefinicaoSenhaRepository pedidos,
                            PasswordEncoder passwordEncoder, RedefinicaoSenhaEmail email, Clock relogio) {
        this.usuarios = usuarios;
        this.pedidos = pedidos;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.relogio = relogio;
    }

    /** Link inválido, vencido ou já usado. A tela pede um link novo. */
    public static class LinkInvalidoException extends RuntimeException {
        public LinkInvalidoException() {
            super("Este link expirou ou já foi usado. Peça um novo.");
        }
    }

    /** Pede o link. Não diz se o e-mail existe: e-mail desconhecido ou pedido demais saem calados. */
    @Transactional
    public void solicitar(String emailInformado) {
        if (emailInformado == null || emailInformado.isBlank()) {
            return;
        }
        Optional<UserEntity> encontrado = usuarios.findByEmail(emailInformado.trim());
        if (encontrado.isEmpty()) {
            return;
        }
        UserEntity usuario = encontrado.get();
        Instant agora = relogio.instant();
        if (pedidos.countByUserIdAndCriadoEmAfter(usuario.getId(), agora.minus(JANELA_PEDIDOS)) >= MAXIMO_PEDIDOS) {
            log.warn("Pedidos de redefinição de senha demais para {}; ignorado", usuario.getEmail());
            return;
        }

        pedidos.encerrarAbertos(usuario.getId(), agora);
        String codigo = novoCodigo();
        pedidos.save(RedefinicaoSenhaEntity.builder()
                .userId(usuario.getId())
                .tokenHash(hash(codigo))
                .criadoEm(agora)
                .expiraEm(agora.plus(VALIDADE))
                .build());
        email.enviarLink(usuario.getEmail(), usuario.getName(), codigo);
    }

    /** O link ainda serve? A tela confere antes de mostrar o formulário. */
    @Transactional(readOnly = true)
    public boolean linkValido(String codigo) {
        return pedidoAberto(codigo).isPresent();
    }

    /** Troca a senha pelo link, encerra os logins abertos e avisa por e-mail. */
    @Transactional
    public void redefinir(String codigo, String novaSenha) {
        validarSenha(novaSenha);
        RedefinicaoSenhaEntity pedido = pedidoAberto(codigo).orElseThrow(LinkInvalidoException::new);
        UserEntity usuario = usuarios.findById(pedido.getUserId()).orElseThrow(LinkInvalidoException::new);

        Instant agora = relogio.instant();
        usuario.setPasswordHash(passwordEncoder.encode(novaSenha));
        usuario.setSenhaAlteradaEm(agora);
        usuarios.save(usuario);
        // Este link e qualquer outro aberto morrem aqui.
        pedidos.encerrarAbertos(usuario.getId(), agora);
        email.avisarTroca(usuario.getEmail(), usuario.getName());
    }

    static void validarSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) {
            throw new IllegalArgumentException("A senha precisa ter pelo menos " + SENHA_MINIMA + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > SENHA_MAXIMA_BYTES) {
            throw new IllegalArgumentException("A senha pode ter no máximo " + SENHA_MAXIMA_BYTES + " caracteres.");
        }
    }

    private Optional<RedefinicaoSenhaEntity> pedidoAberto(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        Instant agora = relogio.instant();
        return pedidos.findByTokenHash(hash(codigo.trim()))
                .filter(p -> p.getUsadoEm() == null && p.getExpiraEm().isAfter(agora));
    }

    static String novoCodigo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String codigo) {
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
