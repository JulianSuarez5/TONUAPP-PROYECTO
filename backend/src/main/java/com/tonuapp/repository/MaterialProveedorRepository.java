package com.tonuapp.repository;

import com.tonuapp.domain.MaterialProveedor;
import com.tonuapp.domain.MaterialProveedorId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialProveedorRepository extends JpaRepository<MaterialProveedor, MaterialProveedorId> {

    // Materiales asociados a un proveedor (N:M, ver ProveedorService)
    List<MaterialProveedor> findByProveedorId(Integer proveedorId);

    // Proveedores asociados a un material (N:M, ver ProveedorService)
    List<MaterialProveedor> findByMaterialId(Integer materialId);

    // Filas de asociacion de varios proveedores, para contar materiales por pagina sin
    // cargar toda la tabla en cada listado paginado de proveedores
    List<MaterialProveedor> findByProveedorIdIn(Collection<Integer> proveedorIds);

    // Asociacion exacta material-proveedor (para actualizar es_principal o desasociar)
    Optional<MaterialProveedor> findByProveedorIdAndMaterialId(Integer proveedorId, Integer materialId);

    // True si la asociacion ya existe (evita duplicar con POST /asociar)
    boolean existsByProveedorIdAndMaterialId(Integer proveedorId, Integer materialId);

    // Cuenta los materiales asociados a un proveedor (RF-010: no se elimina un proveedor
    // con materiales asociados)
    long countByProveedorId(Integer proveedorId);

    // Proveedores marcados como principal de un material (para desmarcarlos antes de
    // asignar un nuevo principal en el N:M)
    List<MaterialProveedor> findByMaterialIdAndEsPrincipalTrue(Integer materialId);
}