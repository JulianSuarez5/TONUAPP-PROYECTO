package com.tonuapp.ubicaciones.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Salida del CRUD de lotes (US-17). Incluye el nombre del material resuelto para que el
 * frontend lo muestre sin exponer la entidad anidada.
 */
public record LoteResponse(
        Integer idLote,
        Integer idMaterial,
        String materialNombre,
        String codigoLote,
        BigDecimal cantidad,
        LocalDateTime fechaIngreso,
        boolean activo
) {
}