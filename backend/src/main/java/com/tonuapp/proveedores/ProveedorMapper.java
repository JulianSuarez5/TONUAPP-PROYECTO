package com.tonuapp.proveedores;

import com.tonuapp.domain.Material;
import com.tonuapp.domain.MaterialProveedor;
import com.tonuapp.domain.Proveedor;
import com.tonuapp.proveedores.dto.MaterialProveedorResponse;
import com.tonuapp.proveedores.dto.ProveedorResponse;

/**
 * Mapeo entre entidades del modulo de proveedores y sus DTOs de salida. Nunca se exponen
 * las entidades directamente a la API (AGENTS.md section 10).
 */
public final class ProveedorMapper {

    private ProveedorMapper() {
    }

    // Convierte un Proveedor en el DTO de salida; cantidadMateriales se inyecta para
    // que el listado muestre de un vistazo si el proveedor abastece materiales
    public static ProveedorResponse toResponse(Proveedor p, long cantidadMateriales) {
        return new ProveedorResponse(
                p.getIdProveedor(),
                p.getNombre(),
                p.getNit(),
                p.getContacto(),
                p.getTelefono(),
                p.getUbicacion(),
                p.isActivo(),
                p.getFechaRegistro(),
                cantidadMateriales);
    }

    // Convierte una fila de material_proveedor en el DTO de salida, resolviendo los nombres
    // del material y del proveedor para el frontend
    public static MaterialProveedorResponse toMaterialProveedorResponse(MaterialProveedor mp,
                                                                        Material material,
                                                                        Proveedor proveedor) {
        return new MaterialProveedorResponse(
                mp.getMaterialId(),
                material.getNombre(),
                mp.getProveedorId(),
                proveedor.getNombre(),
                mp.isEsPrincipal(),
                mp.getFechaAsociacion());
    }
}