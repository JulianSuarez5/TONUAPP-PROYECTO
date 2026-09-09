package com.tonuapp.shared;

import java.time.LocalDateTime;

/**
 * Estructura uniforme de respuesta de error.
 * Formato: { timestamp, status, error, message, path }
 */
public record ApiError(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
    // Builder estatico que pone siempre el timestamp actual al construir el error
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(LocalDateTime.now(), status, error, message, path);
    }
}
