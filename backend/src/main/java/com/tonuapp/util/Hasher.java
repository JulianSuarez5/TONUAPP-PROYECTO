package com.tonuapp.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utilidades de seguridad: hash SHA-256 (hex) y generacion de codigos numericos.
 * Usado para almacenar de forma segura codigos temporales y refresh tokens
 * (nunca en texto plano).
 */
public final class Hasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Hasher() {
    }

    /** SHA-256 en hexadecimal (64 caracteres). */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /** Genera un codigo numerico de 6 digitos (el usuario lo ingresa manualmente). */
    public static String generarCodigoNumerico() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    /** Genera un token aleatorio opaco (usado como refresh token). */
    public static String generarTokenAleatorio() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}