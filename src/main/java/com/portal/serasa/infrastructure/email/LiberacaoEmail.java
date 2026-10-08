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

/**
 * E-mail dos avisos da esteira de liberação.
 *
 * <p>Assíncrono e sem propagar erro: SMTP fora do ar não pode atrasar nem desfazer a ação de quem
 * moveu o card. O sino do portal continua sendo o registro; o e-mail é o alcance fora dele.</p>
 *
 * <p>Desligado por padrão ({@code EMAIL_LIBERACAO_ENABLED}) para ambiente de desenvolvimento não
 * mandar e-mail de teste pelo SMTP real; o docker-compose de produção liga.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiberacaoEmail {

    private final JavaMailSender mailSender;

    @Value("${email.cedente.from}")
    private String remetente;

    @Value("${email.liberacao.enabled:false}")
    private boolean ligado;

    /** Endereço do portal visto pelo navegador, para o link do e-mail. Ex.: http://10.0.0.5:3001 */
    @Value("${app.portal-url:}")
    private String portalUrl;

    @Async
    public void enviar(String para, String nome, String titulo, String resumo, String link) {
        if (!ligado || para == null || para.isBlank() || enderecoDeTeste(para)) {
            return;
        }
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            // Multipart: texto puro e HTML no mesmo e-mail.
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(para);
            helper.setSubject("[Esteira de Liberação] " + titulo);
            String url = urlCompleta(link);
            helper.setText(texto(nome, titulo, resumo, url), html(nome, titulo, resumo, url));
            mailSender.send(mensagem);
        } catch (MessagingException | MailException erro) {
            log.warn("Falha ao enviar aviso da esteira para {}: {}", para, erro.getMessage());
        }
    }

    /** Domínios reservados para teste nunca recebem e-mail de verdade. */
    static boolean enderecoDeTeste(String email) {
        String dominio = email.substring(email.indexOf('@') + 1).toLowerCase();
        return dominio.endsWith(".test") || dominio.endsWith(".local") || dominio.endsWith(".example")
                || dominio.equals("localhost");
    }

    String urlCompleta(String link) {
        if (portalUrl == null || portalUrl.isBlank()) {
            return null;
        }
        return portalUrl.replaceAll("/+$", "") + link;
    }

    private static String texto(String nome, String titulo, String resumo, String url) {
        return "Olá, " + primeiroNome(nome) + ".\n\n" + titulo + "\n" + (resumo == null ? "" : resumo + "\n")
                + "\n" + (url == null ? "Abra a Esteira de Liberação no portal para ver." : "Abrir no portal: " + url)
                + "\n\nVocê recebe este aviso porque está ligado no sino do portal. Para parar, desligue lá.";
    }

    private static String html(String nome, String titulo, String resumo, String url) {
        String botao = url == null
                ? "<p style=\"color:#475569\">Abra a Esteira de Liberação no portal para ver.</p>"
                : "<p style=\"margin:24px 0\"><a href=\"" + HtmlUtils.htmlEscape(url) + "\" "
                + "style=\"background:#612035;color:#ffffff;padding:10px 18px;border-radius:8px;text-decoration:none;font-weight:600\">"
                + "Abrir no portal</a></p>";
        return "<div style=\"font-family:Segoe UI,Arial,sans-serif;font-size:14px;color:#0f172a;max-width:560px\">"
                + "<p>Olá, " + HtmlUtils.htmlEscape(primeiroNome(nome)) + ".</p>"
                + "<p style=\"font-size:16px;font-weight:600;margin:16px 0 4px\">" + HtmlUtils.htmlEscape(titulo) + "</p>"
                + (resumo == null ? "" : "<p style=\"color:#334155;margin:0\">" + HtmlUtils.htmlEscape(resumo) + "</p>")
                + botao
                + "<p style=\"color:#94a3b8;font-size:12px\">Você recebe este aviso porque está ligado no sino do portal. "
                + "Para parar, desligue lá.</p></div>";
    }

    private static String primeiroNome(String nome) {
        return nome == null || nome.isBlank() ? "" : nome.trim().split("\\s+")[0];
    }
}
