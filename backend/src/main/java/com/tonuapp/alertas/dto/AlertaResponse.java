package com.tonuapp.alertas.dto;

import com.tonuapp.domain.AlertaEstado;

import java.time.LocalDateTime;

/**
 * Respuesta de una alerta de bajo stock (RF-012): datos del material en bajo
 * disponibilidad y su estado actual (activa/atendida).
 */
public record AlertaResponse(
        Integer idAlerta,
        Integer idMaterial,
        String nombreMaterial,
        AlertaEstado estado,
        String mensaje,
        LocalDateTime fechaGenerada
) {
}