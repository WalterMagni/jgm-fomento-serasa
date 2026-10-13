package com.portal.serasa.infrastructure.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * E-mails da redefinição de senha: o link para criar a senha nova e o aviso de que ela mudou.
 *
 * <p>Assíncrono e sem propagar erro, como os outros avisos: a resposta da tela é a mesma com ou
 * sem SMTP, para não revelar quem tem conta. Desligado por padrão ({@code EMAIL_SENHA_ENABLED})
 * para o desenvolvimento não mandar e-mail real; aí o caminho do link sai no log, para testar.
 * O docker-compose de produção liga.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedefinicaoSenhaEmail {

    static final int VALIDADE_MINUTOS = 30;

    private final JavaMailSender mailSender;

    @Value("${email.cedente.from}")
    private String remetente;

    @Value("${email.senha.enabled:false}")
    private boolean ligado;

    /** Endereço do portal visto pelo navegador, para o link do e-mail. Ex.: http://10.0.0.5:3001 */
    @Value("${app.portal-url:}")
    private String portalUrl;

    @Async
    public void enviarLink(String para, String nome, String codigo) {
        String caminho = "/redefinir-senha?token=" + URLEncoder.encode(codigo, StandardCharsets.UTF_8);
        if (!ligado) {
            log.info("E-mail de redefinição desligado (EMAIL_SENHA_ENABLED); link para {}: {}", para, caminho);
            return;
        }
        if (portalUrl == null || portalUrl.isBlank()) {
            log.error("APP_PORTAL_URL vazio: sem endereço do portal, o link de redefinição para {} não foi enviado", para);
            return;
        }
        String url = portalUrl.replaceAll("/+$", "") + caminho;
        enviar(para, "Redefinição de senha do Portal JGM", textoLink(nome, url), htmlLink(nome, url));
    }

    @Async
    public void avisarTroca(String para, String nome) {
        if (!ligado) {
            return;
        }
        enviar(para, "Sua senha do Portal JGM foi alterada", textoTroca(nome), htmlTroca(nome));
    }

    private void enviar(String para, String assunto, String texto, String html) {
        if (para == null || para.isBlank() || LiberacaoEmail.enderecoDeTeste(para)) {
            return;
        }
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(para);
            helper.setSubject(assunto);
            helper.setText(texto, html);
            mailSender.send(mensagem);
        } catch (MessagingException | MailException erro) {
            log.warn("Falha ao enviar e-mail de senha para {}: {}", para, erro.getMessage());
        }
    }

    static String textoLink(String nome, String url) {
        return "Olá, " + primeiroNome(nome) + ".\n\n"
                + "Recebemos um pedido para redefinir a sua senha do Portal JGM.\n"
                + "Para criar uma senha nova, abra o link abaixo. Ele vale por " + VALIDADE_MINUTOS + " minutos e só pode ser usado uma vez.\n\n"
                + url + "\n\n"
                + "Se não foi você, ignore este e-mail: a sua senha continua a mesma.";
    }

    static String htmlLink(String nome, String url) {
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#262833;max-width:560px\">"
                + "<p>Olá, " + HtmlUtils.htmlEscape(primeiroNome(nome)) + ".</p>"
                + "<p>Recebemos um pedido para redefinir a sua senha do <b>Portal JGM</b>.</p>"
                + "<p style=\"margin:24px 0\"><a href=\"" + HtmlUtils.htmlEscape(url) + "\" "
                + "style=\"background:#612035;color:#ffffff;padding:11px 20px;border-radius:10px;text-decoration:none;font-weight:600\">"
                + "Criar nova senha</a></p>"
                + "<p style=\"color:#69675F\">O link vale por " + VALIDADE_MINUTOS + " minutos e só pode ser usado uma vez.</p>"
                + "<p style=\"color:#8A877E;font-size:12px\">Se não foi você, ignore este e-mail: a sua senha continua a mesma.</p></div>";
    }

    static String textoTroca(String nome) {
        return "Olá, " + primeiroNome(nome) + ".\n\n"
                + "A sua senha do Portal JGM acabou de ser alterada, e os logins abertos em outros computadores foram encerrados.\n\n"
                + "Se não foi você, avise o administrador do portal imediatamente.";
    }

    static String htmlTroca(String nome) {
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#262833;max-width:560px\">"
                + "<p>Olá, " + HtmlUtils.htmlEscape(primeiroNome(nome)) + ".</p>"
                + "<p>A sua senha do <b>Portal JGM</b> acabou de ser alterada, e os logins abertos em outros computadores foram encerrados.</p>"
                + "<p style=\"color:#8E1B37\"><b>Se não foi você, avise o administrador do portal imediatamente.</b></p></div>";
    }

    private static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "" : nome.trim().split("\\s+")[0];
    }
}
