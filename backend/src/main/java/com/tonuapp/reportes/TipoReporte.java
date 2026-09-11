package com.tonuapp.reportes;

/**
 * Tipos de reporte de inventario (RF-014 / RF-001, RF-004, RF-011, RF-012, RF-013).
 * Nombres en minuscula como el resto de enums del dominio (a juego con los CHECK).
 */
public enum TipoReporte {
    inventario, movimientos, resumen, bajo_stock
}