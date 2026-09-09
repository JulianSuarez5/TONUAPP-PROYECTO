package com.tonuapp.usuarios;

import com.tonuapp.domain.Usuario;
import com.tonuapp.usuarios.dto.UsuarioResponse;

/**
 * Mapeo entre entidad {@link Usuario} y DTO de salida. Nunca se expone la entidad
 * directamente a la API (AGENTS.md section 10).
 */
public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    // Convierte la entidad Usuario en el DTO de salida (con nombre de rol, sin datos internos)
    public static UsuarioResponse toResponse(Usuario u) {
        return new UsuarioResponse(
                u.getIdUsuario(),
                u.getNombre(),
                u.getCorreo(),
                u.getRol().getIdRol(),
                u.getRol().getNombreRol(),
                u.isActivo(),
                u.isEsAdminPrincipal(),
                u.getFechaCreacion());
    }
}