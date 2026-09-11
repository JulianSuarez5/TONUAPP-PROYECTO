package com.tonuapp.reportes;

import java.util.List;

/**
 * Representacion generica de un reporte listo para exportar (RF-014): titulo,
 * subtitulo (periodo consultado), encabezados de columnas y filas alineadas.
 * Permite que el exportador (xlsx/pdf) sea independiente del tipo de reporte.
 */
public record TablaReporte(
        String titulo,
        String subtitulo,
        String[] encabezados,
        List<String[]> filas,
        String notaPie
) {
}