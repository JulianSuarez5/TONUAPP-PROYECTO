package com.tonuapp.materiales.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida del CRUD de materiales (RF-001: nombre, categoria, cantidad disponible y estado).
 * Incluye la ubicacion (idZona/zonaNombre) como indicador de zona de acopio en la pantalla
 * de consulta (RF-015); es nula si el material no esta asignado a ninguna zona.
 */
public record MaterialResponse(
        Integer idMaterial,
        String nombre,
        Integer idCategoria,
        String categoriaNombre,
        Integer idUnidad,
        String unidadNombre,
        String unidadAbreviatura,
        BigDecimal stock,
        BigDecimal stockMinimo,
        boolean activo,
        LocalDateTime fechaRegistro,
        LocalDateTime fechaActualizacion,
        Integer idZona,
        String zonaNombre
) {
}