package com.tonuapp.domain;

/**
 * Tipos de movimiento de inventario (D-02). Los nombres estan en minusculas a proposito:
 * asi, con {@code @Enumerated(STRING)}, coinciden exactamente con los valores del CHECK
 * ck_movimientos_tipo de la tabla movimientos_inventario.
 */
public enum TipoMovimiento {
    entrada, salida_venta, salida_merma, ajuste
}