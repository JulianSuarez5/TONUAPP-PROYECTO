package com.tonuapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reglas de autenticacion por codigo temporal (RF-008 y decision D-12).
 */
@ConfigurationProperties(prefix = "tonuapp.auth")
public class AuthProperties {

    /** Tiempo minimo para permitir un nuevo codigo al mismo correo (D-12). */
    private int codeCooldownSeconds = 60;

    /** Vigencia del codigo temporal en minutos (RF-008: max 10). */
    private int codeExpirationMinutes = 10;

    /** Maximo de intentos fallidos antes de bloquear (a nivel de codigo). */
    private int maxFailedAttempts = 5;

    /** Maximo de verificaciones fallidas por cuenta antes de un bloqueo temporal (RF-008 / D-22). */
    private int loginMaxFailedAttempts = 5;

    /** Duracion del bloqueo temporal por cuenta en minutos (RF-008 / D-22). */
    private int loginLockoutMinutes = 15;

    public int getCodeCooldownSeconds() {
        return codeCooldownSeconds;
    }

    public void setCodeCooldownSeconds(int codeCooldownSeconds) {
        this.codeCooldownSeconds = codeCooldownSeconds;
    }

    public int getCodeExpirationMinutes() {
        return codeExpirationMinutes;
    }

    public void setCodeExpirationMinutes(int codeExpirationMinutes) {
        this.codeExpirationMinutes = codeExpirationMinutes;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getLoginMaxFailedAttempts() {
        return loginMaxFailedAttempts;
    }

    public void setLoginMaxFailedAttempts(int loginMaxFailedAttempts) {
        this.loginMaxFailedAttempts = loginMaxFailedAttempts;
    }

    public int getLoginLockoutMinutes() {
        return loginLockoutMinutes;
    }

    public void setLoginLockoutMinutes(int loginLockoutMinutes) {
        this.loginLockoutMinutes = loginLockoutMinutes;
    }
}