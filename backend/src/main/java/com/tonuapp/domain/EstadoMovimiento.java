package com.tonuapp.domain;

/**
 * Estado de un movimiento de inventario (D-04): los movimientos se anulan
 * (estado = 'anulado'), nunca se borran fisicamente.
 */
public enum EstadoMovimiento {
    activo, anulado
}