package com.tonuapp.movimientos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro de ajuste/conciliacion (RF-011): cantidad anterior y nueva, motivo y autor.
 * La UI lo usa para revisar la historia de conciliaciones y saber que reversa esperar
 * al anular un movimiento 'ajuste' (D-19).
 */
public record AjusteResponse(
        Integer idAjuste,
        Integer idMaterial,
        String nombreMaterial,
        Integer idUsuario,
        String nombreUsuario,
        Integer idMovimiento,
        BigDecimal cantidadAnterior,
        BigDecimal cantidadNueva,
        String motivo,
        LocalDateTime fechaAjuste
) {
}