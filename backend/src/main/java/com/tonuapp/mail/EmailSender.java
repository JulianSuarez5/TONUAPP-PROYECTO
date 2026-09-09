package com.tonuapp.mail;

/**
 * Contrato del envio de correos. La implementacion real es {@link SmtpEmailSender}
 * (D-39): envia el codigo por SMTP y lo conserva en el log como respaldo.
 */
public interface EmailSender {

    /**
     * Envia el codigo de acceso temporal al correo del usuario.
     */
    void sendAccessCode(String to, String code);
}