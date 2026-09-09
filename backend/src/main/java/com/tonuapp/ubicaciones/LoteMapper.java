package com.tonuapp.ubicaciones;

import com.tonuapp.domain.Lote;
import com.tonuapp.ubicaciones.dto.LoteResponse;

/**
 * Mapeo entre la entidad {@link Lote} y su DTO de salida. Nunca se expone la entidad
 * directamente a la API (AGENTS.md section 10).
 */
public final class LoteMapper {

    private LoteMapper() {
    }

    // Convierte un lote en su DTO, resolviendo el nombre del material para el frontend
    public static LoteResponse toResponse(Lote l) {
        return new LoteResponse(
                l.getIdLote(),
                l.getMaterial().getIdMaterial(),
                l.getMaterial().getNombre(),
                l.getCodigoLote(),
                l.getCantidad(),
                l.getFechaIngreso(),
                l.isActivo());
    }
}