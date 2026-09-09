package com.tonuapp.proveedores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada del CRUD de proveedores (RF-010). Solo nombre y nit son obligatorios;
 * contacto/telefono/ubicacion son opcionales.
 */
public record ProveedorRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 150, message = "El nombre no puede superar 150 caracteres.")
        String nombre,

        @NotBlank(message = "El NIT es obligatorio.")
        @Size(max = 20, message = "El NIT no puede superar 20 caracteres.")
        String nit,

        @Size(max = 100, message = "El contacto no puede superar 100 caracteres.")
        String contacto,

        @Size(max = 20, message = "El telefono no puede superar 20 caracteres.")
        String telefono,

        @Size(max = 200, message = "La ubicacion no puede superar 200 caracteres.")
        String ubicacion
) {
}