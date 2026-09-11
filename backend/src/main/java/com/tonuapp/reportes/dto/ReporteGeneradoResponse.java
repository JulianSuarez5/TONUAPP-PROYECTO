package com.tonuapp.reportes.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entrada de la bitacora de exportaciones (RF-014): QUIEN pidio el reporte, CUANDO,
 * de que tipo/formato, con que rango y a que archivo apunta. El binario no vive en
 * la BD; se recupera por /api/reportes/{id}/descargar.
 */
public record ReporteGeneradoResponse(
        Integer idReporte,
        Integer idUsuario,
        String tipoReporte,
        String formato,
        LocalDateTime fechaGeneracion,
        LocalDate rangoFechaInicio,
        LocalDate rangoFechaFin,
        String nombreArchivo
) {
}