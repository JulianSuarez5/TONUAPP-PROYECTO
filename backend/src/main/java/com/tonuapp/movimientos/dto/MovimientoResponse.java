package com.tonuapp.movimientos.dto;

import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.TipoMovimiento;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Respuesta de un movimiento: datos del movimiento (no las entidades anidadas) y el
 * nombre del material y del usuario resueltos para la UI.
 */
public record MovimientoResponse(
        Integer idMovimiento,
        Integer idMaterial,
        String nombreMaterial,
        Integer idUsuario,
        String nombreUsuario,
        TipoMovimiento tipo,
        BigDecimal cantidad,
        EstadoMovimiento estado,
        String motivo,
        String observaciones,
        Integer idLote,
        Integer idZona,
        LocalDateTime fechaMovimiento
) {
}