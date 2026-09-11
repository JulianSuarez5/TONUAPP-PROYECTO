package com.tonuapp.reportes.dto;

import com.tonuapp.reportes.TipoReporte;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Solicitud para exportar un reporte (RF-014): tipo de reporte, formato (pdf/xlsx)
 * y rango de fechas opcional. El formato se valida en el service (mantiene el error
 * acotado al negocio); las fechas deben venir ambas o ninguna, y desde <= hasta.
 */
public record ReporteRequest(
        @NotNull(message = "El tipo de reporte es obligatorio.")
        TipoReporte tipo,

        @NotBlank(message = "El formato es obligatorio.")
        String formato,

        LocalDate desde,
        LocalDate hasta
) {
}