package com.tonuapp.movimientos.dto;

import com.tonuapp.domain.TipoMovimiento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request para registrar una ENTRADA o SALIDA (venta/merma). Los ajustes no van aqui:
 * usan {@link AjusteRequest} (stock objetivo, RF-011). La cantidad siempre es positiva
 * y mayor que cero; el signo del efecto lo decide el service segun el tipo (D-02).
 * idLote/idZona son opcionales hasta la fase de Ubicaciones y lotes.
 */
public record MovimientoRequest(
        @NotNull(message = "El material es obligatorio.")
        Integer idMaterial,

        @NotNull(message = "El tipo de movimiento es obligatorio.")
        TipoMovimiento tipo,

        @NotNull(message = "La cantidad es obligatoria.")
        @DecimalMin(value = "0.01", message = "La cantidad debe ser mayor que 0.")
        BigDecimal cantidad,

        String motivo,
        String observaciones,
        Integer idLote,
        Integer idZona
) {
}