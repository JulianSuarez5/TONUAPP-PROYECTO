package com.tonuapp.usuarios.dto;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Integer idUsuario,
        String nombre,
        String correo,
        Integer idRol,
        String nombreRol,
        boolean activo,
        boolean esAdminPrincipal,
        LocalDateTime fechaCreacion
) {
}