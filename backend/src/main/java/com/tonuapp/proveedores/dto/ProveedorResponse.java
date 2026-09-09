package com.tonuapp.proveedores.dto;

import java.time.LocalDateTime;

/**
 * Salida del CRUD de proveedores (RF-010). Incluye cantidadMateriales para que el frontend
 * muestre de un vistazo si el proveedor abastece materiales (y por que no se puede borrar).
 */
public record ProveedorResponse(
        Integer idProveedor,
        String nombre,
        String nit,
        String contacto,
        String telefono,
        String ubicacion,
        boolean activo,
        LocalDateTime fechaRegistro,
        long cantidadMateriales
) {
}