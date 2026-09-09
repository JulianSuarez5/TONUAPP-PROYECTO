package com.tonuapp.ubicaciones.dto;

import java.math.BigDecimal;

/**
 * Salida del CRUD de zonas de acopio (RF-015). cantidadMateriales permite mostrar de un
 * vistazo cuantos materiales activos ocupan la zona (y por que no se puede borrar).
 */
public record ZonaAcopioResponse(
        Integer idZona,
        String nombreZona,
        BigDecimal capacidadMaxima,
        String tipoMaterialPermitido,
        boolean activo,
        long cantidadMateriales
) {
}