package com.tonuapp.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.config.MailProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

/**
 * Pruebas de {@link SmtpEmailSender} (D-39): el codigo SIEMPRE queda en el log y el
 * envio por SMTP nunca rompe el flujo de login.
 */
class SmtpEmailSenderTest {

    private JavaMailSender mailSender;
    private MailProperties props;
    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        props = new MailProperties();
        sender = new SmtpEmailSender(mailSender, props);

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
    }

    @Test
    @DisplayName("mail deshabilitado: no intenta enviar y no lanza (el codigo queda en el log)")
    void disabledDoesNotSend() {
        props.setEnabled(false);

        sender.sendAccessCode("destino@test.com", "123456");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("mail habilitado sin credenciales: no intenta enviar y no lanza")
    void enabledWithoutCredentialsDoesNotSend() {
        props.setEnabled(true);
        props.setUsername("");
        props.setPassword("");

        sender.sendAccessCode("destino@test.com", "123456");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("envio exitoso: genera MimeMessage con asunto y destinatario, y envia")
    void sendAccessCodeSendsEmail() throws Exception {
        props.setEnabled(true);
        props.setUsername("juakosuarez12@gmail.com");
        props.setPassword("x");
        props.setFrom("juakosuarez12@gmail.com");

        sender.sendAccessCode("destino@test.com", "654321");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("fallo del SMTP: se captura y NO se propaga (el codigo queda en el log)")
    void smtpFailureDoesNotPropagate() {
        props.setEnabled(true);
        props.setUsername("juakosuarez12@gmail.com");
        props.setPassword("x");
        props.setFrom("juakosuarez12@gmail.com");
        doThrow(new MailSendException("smtp caido")).when(mailSender).send(any(MimeMessage.class));

        sender.sendAccessCode("destino@test.com", "123456");

        verify(mailSender).send(any(MimeMessage.class));
    }
}