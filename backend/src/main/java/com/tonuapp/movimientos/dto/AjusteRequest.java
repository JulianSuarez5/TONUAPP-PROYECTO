package com.tonuapp.movimientos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request para un AJUSTE de inventario (RF-011): la entrada es la cantidad fisica real
 * contada (stock objetivo). El service calcula la diferencia con el stock actual y la
 * registra como movimiento 'ajuste' con cantidad = |diferencia| (decision aceptada
 * por el usuario: D-18; la traza fina en ajustes_inventario se completa en la fase
 * "Ajustes y mermas").
 */
public record AjusteRequest(
        @NotNull(message = "El material es obligatorio.")
        Integer idMaterial,

        @NotNull(message = "La cantidad contada es obligatoria.")
        @DecimalMin(value = "0.00", message = "La cantidad no puede ser negativa.")
        BigDecimal cantidadNueva,

        @NotBlank(message = "motivo: El motivo del ajuste es obligatorio.")
        String motivo
) {
}