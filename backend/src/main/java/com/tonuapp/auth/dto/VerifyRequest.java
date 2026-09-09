package com.tonuapp.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "Correo invalido")
        String correo,

        @NotBlank(message = "El codigo es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "El codigo debe ser de 6 digitos")
        String codigo
) {
}