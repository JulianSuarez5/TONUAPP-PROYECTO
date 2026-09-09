package com.tonuapp.usuarios.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRolRequest(
        @NotNull(message = "El rol es obligatorio")
        Integer idRol
) {
}