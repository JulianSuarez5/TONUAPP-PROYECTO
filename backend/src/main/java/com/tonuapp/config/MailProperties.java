package com.tonuapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del envio de correos por SMTP (RF-008).
 * Las credenciales se leen de la variables de entorno TONUAPP_MAIL_* (o de
 * application-local.yml, que esta en .gitignore). Nunca se hardcodean ni se versionan.
 */
@ConfigurationProperties(prefix = "tonuapp.mail")
public class MailProperties {

    private boolean enabled = false;
    private String host = "smtp.gmail.com";
    private int port = 587;
    private String username = "";
    private String password = "";
    private String from = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    /** Si falta remitente o credenciales, el envio real no es posible. */
    public boolean isConfigurado() {
        return enabled && !username.isBlank() && !password.isBlank();
    }
}