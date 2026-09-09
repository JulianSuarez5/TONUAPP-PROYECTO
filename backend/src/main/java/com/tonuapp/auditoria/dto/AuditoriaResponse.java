package com.tonuapp.auditoria.dto;

import java.time.LocalDateTime;

/**
 * Un registro del log de auditoria listo para exponerse en GET /api/auditoria.
 * Incluye el autor (nombre/correo) gracias al join solo-lectura de AuditLog->Usuario
 * (Fase 10 / D-22).
 */
public record AuditoriaResponse(
        Long id,
        String entidad,
        String idRegistro,
        String operacion,
        Integer idUsuario,
        String nombreUsuario,
        String correoUsuario,
        LocalDateTime fecha,
        String valoresAntes,
        String valoresDespues) {
}