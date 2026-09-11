package com.tonuapp.reportes.dto;

import java.math.BigDecimal;

/**
 * Fila del reporte resumen por material (RF-013/RF-004) en un rango de fechas.
 * ajusteNeto suma el DELTA de cada ajuste (cantidadNueva - cantidadAnterior, D-19)
 * para que el efecto neto sea consistente; el delta del ajuste tambien se resume.
 * efectoNeto = entradas - salidasVenta - salidasMerma + ajusteNeto.
 */
public record ReporteResumenRow(
        Integer idMaterial,
        String nombreMaterial,
        BigDecimal entradas,
        BigDecimal salidasVenta,
        BigDecimal salidasMerma,
        BigDecimal ajusteNeto,
        BigDecimal efectoNeto,
        BigDecimal stockActual
) {
}