package com.portal.serasa.infrastructure.email;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiberacaoEmailTest {

    @Mock private JavaMailSender mailSender;

    private LiberacaoEmail email;

    @BeforeEach
    void setUp() {
        email = new LiberacaoEmail(mailSender);
        ReflectionTestUtils.setField(email, "remetente", "portal@jgm.com");
        ReflectionTestUtils.setField(email, "ligado", true);
        ReflectionTestUtils.setField(email, "portalUrl", "http://10.0.0.5:3001");
    }

    // ------------------------------------------------------- endereços de teste

    @ParameterizedTest
    @ValueSource(strings = {"ana@empresa.test", "ana@portal.local", "ana@dominio.example", "ana@localhost",
            "ANA@EMPRESA.TEST", "ana@Portal.LOCAL", "ana@sub.dominio.example", "ana@LOCALHOST"})
    @DisplayName("enderecoDeTeste: *.test, *.local, *.example e localhost são de teste")
    void shouldRecognizeReservedTestDomains(String endereco) {
        assertThat(LiberacaoEmail.enderecoDeTeste(endereco)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@jgm.com", "ana@gmail.com", "ana@test.com", "ana@mylocal.com", "ana@localhost.com",
            "ana@contest", "ana@empresa.com.br"})
    @DisplayName("enderecoDeTeste: domínio de verdade não é de teste, nem quando só parece")
    void shouldNotTreatRealDomainsAsTest(String endereco) {
        assertThat(LiberacaoEmail.enderecoDeTeste(endereco)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@empresa.test", "ana@portal.local", "ana@dominio.example", "ana@localhost"})
    @DisplayName("enviar: endereço de domínio de teste nunca é enviado, mesmo com o e-mail ligado")
    void shouldNeverSendToTestAddresses(String endereco) {
        email.enviar(endereco, "Ana Souza", "#42 finalizado", "ACME LTDA", "/liberacao?card=1");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    // ------------------------------------------------------------- desligado

    @Test
    @DisplayName("enviar: com EMAIL_LIBERACAO_ENABLED desligado não monta nem envia nada")
    void shouldNotSendWhenDisabled() {
        ReflectionTestUtils.setField(email, "ligado", false);

        email.enviar("ana@jgm.com", "Ana Souza", "#42 finalizado", "ACME LTDA", "/liberacao?card=1");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("enviar: destinatário em branco é ignorado")
    void shouldIgnoreBlankRecipient(String endereco) {
        email.enviar(endereco, "Ana Souza", "#42 finalizado", "ACME LTDA", "/liberacao?card=1");

        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    @DisplayName("enviar: destinatário nulo é ignorado")
    void shouldIgnoreNullRecipient() {
        assertThatCode(() -> email.enviar(null, "Ana Souza", "#42 finalizado", "ACME LTDA", "/liberacao?card=1"))
                .doesNotThrowAnyException();

        verify(mailSender, never()).createMimeMessage();
    }

    // ---------------------------------------------------------------- envio

    private MimeMessage mensagemVazia() {
        MimeMessage mensagem = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mensagem);
        return mensagem;
    }

    /**
     * BUG DE PRODUÇÃO, não do teste: LiberacaoEmail.java:48 cria o MimeMessageHelper com
     * multipart=false e a linha 53 chama setText(texto, html), que só existe em modo multipart.
     * Resultado: IllegalStateException ("Not in multipart mode...") em toda chamada que passa pelas
     * guardas, o e-mail nunca sai e a exceção (que não é MessagingException nem MailException)
     * escapa do catch. Reative este teste quando o helper for criado com multipart=true.
     */
    @Test
    @DisplayName("enviar: com e-mail ligado e endereço real monta a mensagem (remetente, destinatário, assunto) e envia")
    void shouldSendMessageToRealAddress() throws Exception {
        MimeMessage mensagem = mensagemVazia();

        email.enviar("ana@jgm.com", "Ana Souza", "#42 finalizado (aprovado) por Andressa", "ACME LTDA",
                "/liberacao?card=42");

        ArgumentCaptor<MimeMessage> enviada = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(enviada.capture());
        assertThat(enviada.getValue()).isSameAs(mensagem);
        assertThat(mensagem.getSubject()).isEqualTo("[Esteira de Liberação] #42 finalizado (aprovado) por Andressa");
        assertThat(mensagem.getFrom()[0].toString()).isEqualTo("portal@jgm.com");
        assertThat(mensagem.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("ana@jgm.com");
    }

    @Test
    @DisplayName("enviar: falha do SMTP é engolida, não propaga para quem moveu o card")
    void shouldSwallowSmtpFailure() {
        mensagemVazia();
        org.mockito.Mockito.doThrow(new MailSendException("smtp fora")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> email.enviar("ana@jgm.com", "Ana Souza", "#42", "ACME", "/liberacao?card=42"))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ link

    @Test
    @DisplayName("urlCompleta: junta o endereço do portal ao link, sem barra duplicada")
    void shouldJoinPortalUrlAndLink() {
        assertThat(email.urlCompleta("/liberacao?card=42")).isEqualTo("http://10.0.0.5:3001/liberacao?card=42");

        ReflectionTestUtils.setField(email, "portalUrl", "http://10.0.0.5:3001///");
        assertThat(email.urlCompleta("/liberacao?card=42")).isEqualTo("http://10.0.0.5:3001/liberacao?card=42");
    }

    @Test
    @DisplayName("urlCompleta: sem endereço do portal configurado não há link")
    void shouldHaveNoLinkWithoutPortalUrl() {
        ReflectionTestUtils.setField(email, "portalUrl", "");
        assertThat(email.urlCompleta("/liberacao?card=42")).isNull();

        ReflectionTestUtils.setField(email, "portalUrl", null);
        assertThat(email.urlCompleta("/liberacao?card=42")).isNull();
    }
}
