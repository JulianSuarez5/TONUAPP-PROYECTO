package com.tonuapp.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo invalido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String correo,

        @NotNull(message = "El rol es obligatorio")
        Integer idRol
) {
}