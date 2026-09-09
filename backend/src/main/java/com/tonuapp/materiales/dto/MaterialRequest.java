package com.tonuapp.materiales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Entrada del CRUD de materiales (RF-001/RF-002). El stock inicial se materializa en la
 * creacion (D-17); los movimientos (Fase 6) lo mantendran actualizado.
 */
public record MaterialRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres.")
        String nombre,

        @NotNull(message = "La categoria es obligatoria.")
        Integer idCategoria,

        @NotNull(message = "La unidad de medida es obligatoria.")
        Integer idUnidad,

        @DecimalMin(value = "0.0", message = "El stock inicial no puede ser negativo.")
        BigDecimal stock,

        @DecimalMin(value = "0.0", message = "El stock minimo no puede ser negativo.")
        BigDecimal stockMinimo
) {
}