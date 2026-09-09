package com.tonuapp.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Utilidades para acceder al usuario autenticado desde el SecurityContext.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    // Devuelve el usuario autenticado del SecurityContext (vacio si no hay sesion)
    public static Optional<AuthenticatedUser> currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }
}