package com.tonuapp.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Manejo centralizado de errores .
 * Todos los errores devuelven una respuesta uniforme {@link ApiError}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex, HttpServletRequest request) {
        HttpStatus status = ex.getStatus();
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Errores de validacion de beans (Bean Validation). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Validacion fallida");
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Acceso denegado por @PreAuthorize (403). No confundir con autenticacion (401). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        ApiError body = ApiError.of(status.value(), "Forbidden",
                "No tiene permisos para esta operación.", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Tipo de contenido no soportado (415). Mismo principio que AccessDenied. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(),
                "Tipo de contenido no soportado. Use application/json.", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Parametros de tipo incorrecto (ej. id no numerico en la URL). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        String message = "Parametro invalido: " + ex.getName();
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Recurso no encontrado (404). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), "Recurso no encontrado", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Violacion de integridad referencial/unicidad (ej. duplicados, FK). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(),
                "Conflicto con datos existentes (posible duplicado o restriccion de integridad)", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /** Lock optimista (D-03): otra operacion modifico la misma fila casi a la vez (409). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(),
                "Conflicto de concurrencia: el registro fue modificado por otra operacion. Reintente.", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Cualquier otra excepcion no contemplada (500).
     * La respuesta al cliente es generica (sin exponer detalles), pero se registra la
     * excepcion completa (mensaje + stack trace) para dejar trazabilidad en el servidor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(),
                "Error interno del servidor", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}

