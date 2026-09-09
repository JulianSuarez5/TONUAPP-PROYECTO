package com.tonuapp.domain;

/**
 * Estado de una alerta de bajo stock (RF-012). Nombres en minuscula a juego con el
 * CHECK de alertas_inventario (activa, atendida). El cierre es MANUAL por el
 * Administrador (D-20): 'atendida' implica que alguien la confirmo/actuo.
 */
public enum AlertaEstado {
    activa,
    atendida
}