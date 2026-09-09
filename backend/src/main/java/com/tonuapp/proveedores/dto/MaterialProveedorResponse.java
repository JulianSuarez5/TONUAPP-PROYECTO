package com.tonuapp.proveedores.dto;

import java.time.LocalDateTime;

/**
 * Salida de una asociacion material-proveedor (N:M, D-14). Incluye nombre de material y de
 * proveedor resueltos para que el frontend no haga llamadas extra.
 */
public record MaterialProveedorResponse(
        Integer idMaterial,
        String nombreMaterial,
        Integer idProveedor,
        String nombreProveedor,
        boolean esPrincipal,
        LocalDateTime fechaAsociacion
) {
}