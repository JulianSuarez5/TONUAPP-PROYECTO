package com.tonuapp.shared;

import org.springframework.http.HttpStatus;

/**
 * Excepcion de negocio con un codigo HTTP asociado. Las capas de servicio lanzan
 * esta excepcion; el GlobalExceptionHandler la traduce a un ApiError uniforme.
 * Evita try/catch repetido en los controllers.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
