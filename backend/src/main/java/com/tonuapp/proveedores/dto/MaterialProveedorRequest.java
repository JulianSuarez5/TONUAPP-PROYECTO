package com.tonuapp.proveedores.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Entrada para asociar un material a un proveedor (N:M). esPrincipal marca al proveedor
 * principal de ese material (D-01).
 */
public record MaterialProveedorRequest(
        @NotNull(message = "El material es obligatorio.")
        Integer idMaterial,

        Boolean esPrincipal
) {
}