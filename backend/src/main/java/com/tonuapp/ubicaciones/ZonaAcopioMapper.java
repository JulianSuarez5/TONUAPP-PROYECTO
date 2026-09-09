package com.tonuapp.ubicaciones;

import com.tonuapp.domain.ZonaAcopio;
import com.tonuapp.ubicaciones.dto.ZonaAcopioResponse;

/**
 * Mapeo entre la entidad {@link ZonaAcopio} y su DTO de salida. Nunca se expone la
 * entidad directamente a la API (AGENTS.md section 10).
 */
public final class ZonaAcopioMapper {

    private ZonaAcopioMapper() {
    }

    // Convierte una zona en su DTO; cantidadMateriales se inyecta para que el listado
    // muestre de un vistazo cuantos materiales activos ocupan la zona
    public static ZonaAcopioResponse toResponse(ZonaAcopio z, long cantidadMateriales) {
        return new ZonaAcopioResponse(
                z.getIdZona(),
                z.getNombreZona(),
                z.getCapacidadMaxima(),
                z.getTipoMaterialPermitido(),
                z.isActivo(),
                cantidadMateriales);
    }
}