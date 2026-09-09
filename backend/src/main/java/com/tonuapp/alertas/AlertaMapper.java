package com.tonuapp.alertas;

import com.tonuapp.alertas.dto.AlertaResponse;
import com.tonuapp.domain.AlertaInventario;

/**
 * Mapeo entre entidad {@link AlertaInventario} y DTO de salida. Nunca se expone la
 * entidad directamente a la API (AGENTS.md section 10).
 */
public final class AlertaMapper {

    private AlertaMapper() {
    }

    // Convierte la alerta en el DTO de salida con el material resuelto
    public static AlertaResponse toResponse(AlertaInventario a) {
        return new AlertaResponse(
                a.getIdAlerta(),
                a.getMaterial().getIdMaterial(),
                a.getMaterial().getNombre(),
                a.getEstado(),
                a.getMensaje(),
                a.getFechaGenerada());
    }
}