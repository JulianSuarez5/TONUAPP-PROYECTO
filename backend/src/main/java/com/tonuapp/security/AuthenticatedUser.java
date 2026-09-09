package com.tonuapp.security;

import java.io.Serializable;

/**
 * Usuario autenticado (principal) extraido del JWT. Por convencion de Spring Security
 * todas las autoridades se prefijan con "ROLE_".
 */
public record AuthenticatedUser(Integer idUsuario, String correo, String rol) implements Serializable {

    // Convierte el nombre de rol a autoridad Spring Security (prefijo ROLE_)
    public static String authority(String rol) {
        return "ROLE_" + rol;
    }
}