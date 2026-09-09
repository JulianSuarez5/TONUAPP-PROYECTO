package com.tonuapp.ubicaciones.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Entrada del CRUD de zonas de acopio (RF-015). nombre_zona y capacidad_maxima son
 * obligatorios; tipo_material_permitido es opcional (si se define, la zona solo admite
 * materiales de esa categoria - validacion RF-015).
 */
public record ZonaAcopioRequest(
        @NotBlank(message = "El nombre de la zona es obligatorio.")
        @Size(max = 100, message = "El nombre de la zona no puede superar 100 caracteres.")
        String nombreZona,

        @NotNull(message = "La capacidad maxima es obligatoria.")
        @DecimalMin(value = "0.0", message = "La capacidad maxima no puede ser negativa.")
        @DecimalMax(value = "999999999999.99", message = "La capacidad maxima es demasiado grande.")
        BigDecimal capacidadMaxima,

        @Size(max = 100, message = "El tipo de material permitido no puede superar 100 caracteres.")
        String tipoMaterialPermitido
) {
}