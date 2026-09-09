package com.tonuapp.ubicaciones.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Entrada del CRUD de lotes (US-17). La cantidad es SOLO informativa (D-02): el stock
 * real se calcula desde movimientos, por lo que este campo no altera la existencia.
 */
public record LoteRequest(
        @NotNull(message = "El material es obligatorio.")
        Integer idMaterial,

        @NotBlank(message = "El codigo del lote es obligatorio.")
        @Size(max = 50, message = "El codigo del lote no puede superar 50 caracteres.")
        String codigoLote,

        @NotNull(message = "La cantidad del lote es obligatoria.")
        @DecimalMin(value = "0.0", message = "La cantidad del lote no puede ser negativa.")
        BigDecimal cantidad
) {
}