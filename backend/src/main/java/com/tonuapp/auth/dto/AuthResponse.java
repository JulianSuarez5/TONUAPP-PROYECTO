package com.tonuapp.auth.dto;

import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        Instant accessTokenExpira,
        Instant refreshTokenExpira,
        Integer idUsuario,
        String correo,
        String rol
) {
}