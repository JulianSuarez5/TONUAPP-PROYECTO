package com.tonuapp.reportes.dto;

import java.math.BigDecimal;

/**
 * Fila de los reportes de inventario (RF-001) y bajo stock (RF-012): stock actual,
 * minimo y si el material tiene una alerta activa. Bajo stock devuelve solo los que
 * estan por debajo del minimo, ordenados por stock ascendente.
 */
public record ReporteInventarioRow(
        Integer idMaterial,
        String nombreMaterial,
        String categoria,
        String unidad,
        BigDecimal stock,
        BigDecimal stockMinimo,
        boolean conAlertaActiva
) {
}