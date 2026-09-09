package com.tonuapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestCodeRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo invalido")
        String correo
) {
}