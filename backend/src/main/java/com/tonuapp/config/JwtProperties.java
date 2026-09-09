package com.tonuapp.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

/**
 * Configuracion JWT (decision D-10). La clave se lee de la variable de entorno
 * TONUAPP_JWT_SECRET (o de application-local.yml, que esta en .gitignore). Nunca se
 * hardcodea ni se versiona. Se valida en el arranque: minimo 256 bits (32 bytes) para
 * HS256.
 */
@ConfigurationProperties(prefix = "tonuapp.jwt")
public class JwtProperties {

    private String secret = "";
    private int accessExpirationMinutes = 15;
    private int refreshExpirationDays = 7;

    // Valida en el arranque que la clave exista y tenga >= 32 bytes para HS256
    @PostConstruct
    void validate() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "La clave de firma JWT no esta configurada. Defina TONUAPP_JWT_SECRET "
                            + "(o el valor en application-local.yml). Ver D-10 en DECISIONES.md.");
        }
        int bytes = secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 32) {
            throw new IllegalStateException(
                    "La clave de firma JWT debe tener al menos 256 bits (32 bytes) para HS256. "
                            + "La configurada tiene " + bytes + " bytes.");
        }
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getAccessExpirationMinutes() {
        return accessExpirationMinutes;
    }

    public void setAccessExpirationMinutes(int accessExpirationMinutes) {
        this.accessExpirationMinutes = accessExpirationMinutes;
    }

    public int getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    public void setRefreshExpirationDays(int refreshExpirationDays) {
        this.refreshExpirationDays = refreshExpirationDays;
    }
}