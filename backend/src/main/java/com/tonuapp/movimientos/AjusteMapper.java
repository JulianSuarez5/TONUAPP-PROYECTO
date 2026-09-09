package com.tonuapp.movimientos;

import com.tonuapp.domain.AjusteInventario;
import com.tonuapp.movimientos.dto.AjusteResponse;

/**
 * Mapeo entre entidad {@link AjusteInventario} y DTO de salida. Nunca se expone la
 * entidad directamente a la API (AGENTS.md section 10).
 */
public final class AjusteMapper {

    private AjusteMapper() {
    }

    // Convierte el ajuste en el DTO de salida con material/usuario/movimiento resueltos
    public static AjusteResponse toResponse(AjusteInventario a) {
        return new AjusteResponse(
                a.getIdAjuste(),
                a.getMaterial().getIdMaterial(),
                a.getMaterial().getNombre(),
                a.getUsuario().getIdUsuario(),
                a.getUsuario().getNombre(),
                a.getMovimiento() != null ? a.getMovimiento().getIdMovimiento() : null,
                a.getCantidadAnterior(),
                a.getCantidadNueva(),
                a.getMotivo(),
                a.getFechaAjuste());
    }
}