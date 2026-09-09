package com.tonuapp.movimientos;

import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.movimientos.dto.MovimientoResponse;

/**
 * Mapeo entre entidad {@link MovimientoInventario} y DTO de salida. Nunca se expone la
 * entidad directamente a la API (AGENTS.md section 10).
 */
public final class MovimientoMapper {

    private MovimientoMapper() {
    }

    // Convierte el movimiento en el DTO de salida con material/usuario resueltos
    public static MovimientoResponse toResponse(MovimientoInventario m) {
        return new MovimientoResponse(
                m.getIdMovimiento(),
                m.getMaterial().getIdMaterial(),
                m.getMaterial().getNombre(),
                m.getUsuario().getIdUsuario(),
                m.getUsuario().getNombre(),
                m.getTipoMovimiento(),
                m.getCantidad(),
                m.getEstado(),
                m.getMotivo(),
                m.getObservaciones(),
                m.getIdLote(),
                m.getIdZona(),
                m.getFechaMovimiento());
    }
}