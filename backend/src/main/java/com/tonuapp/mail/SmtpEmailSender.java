package com.tonuapp.mail;

import com.tonuapp.config.MailProperties;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Envio REAL del codigo de acceso por SMTP (RF-008, decision D-39). Reemplaza a
 * {@code StubEmailSender}: mantiene el codigo SIEMPRE en el log (respaldo si el envio
 * falla) y ademas intenta entregarlo por correo usando {@link JavaMailSender}.
 * Si el SMTP falla (sin internet, credenciales invalidas, etc.) se captura el error,
 * se registra y NO rompe el flujo de login: el codigo ya quedo en el log.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final MailProperties props;

    public SmtpEmailSender(JavaMailSender mailSender, MailProperties props) {
        this.mailSender = mailSender;
        this.props = props;
    }

    @Override
    public void sendAccessCode(String to, String code) {
        // Respaldo: el codigo SIEMPRE queda en el log, aunque el correo no salga
        log.info("[DEV] Codigo de acceso para {}: {}", to, code);

        if (!props.isConfigurado()) {
            log.warn("SMTP no configurado (TONUAPP_MAIL_ENABLED o credenciales ausentes). "
                    + "El codigo solo quedo en el log; ver D-39 en DECISIONES.md.");
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(props.getFrom().isBlank() ? props.getUsername() : props.getFrom());
            helper.setTo(to);
            helper.setSubject("Tu codigo de acceso - TONUAPP");
            helper.setText(htmlBody(to, code), true);
            mailSender.send(message);
            log.info("Codigo de acceso enviado por correo a {}", to);
        } catch (Exception ex) {
            // No propaga: el login no se rompe y el codigo ya esta en el log
            log.error("No se pudo enviar el correo a {} (el codigo esta en el log): {}", to, ex.getMessage(), ex);
        }
    }

    private String htmlBody(String to, String code) {
        return "<p>Hola, te enviamos tu codigo de acceso a <b>TONUAPP</b>.</p>"
                + "<p>Codigo: <b style=\"font-size:1.4em;letter-spacing:4px\">" + code + "</b></p>"
                + "<p>Vence en 10 minutos. Si no solicitaste este codigo, ignora este correo.</p>";
    }
}