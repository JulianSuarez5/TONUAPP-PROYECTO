package com.tonuapp.materiales;

import com.tonuapp.domain.Material;
import com.tonuapp.materiales.dto.MaterialResponse;

/**
 * Mapeo entre entidad {@link Material} y DTO de salida. Nunca se expone la entidad
 * directamente a la API (AGENTS.md section 10).
 */
public final class MaterialMapper {

    private MaterialMapper() {
    }

    // Convierte la entidad Material en el DTO que sale por la API (con nombres de
    // categoria/unidad resueltos para no exponer las entidades anidadas). Incluye la
    // zona de acopio (RF-015): nula si el material no esta asignado a ninguna zona
    public static MaterialResponse toResponse(Material m) {
        Integer idZona = m.getZona() != null ? m.getZona().getIdZona() : null;
        String zonaNombre = m.getZona() != null ? m.getZona().getNombreZona() : null;
        return new MaterialResponse(
                m.getIdMaterial(),
                m.getNombre(),
                m.getCategoria().getIdCategoria(),
                m.getCategoria().getNombre(),
                m.getUnidad().getIdUnidad(),
                m.getUnidad().getNombre(),
                m.getUnidad().getAbreviatura(),
                m.getStock(),
                m.getStockMinimo(),
                m.isActivo(),
                m.getFechaRegistro(),
                m.getFechaActualizacion(),
                idZona,
                zonaNombre);
    }
}