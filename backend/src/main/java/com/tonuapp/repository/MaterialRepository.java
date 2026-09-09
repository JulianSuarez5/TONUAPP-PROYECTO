package com.tonuapp.repository;

import com.tonuapp.domain.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialRepository extends JpaRepository<Material, Integer>, JpaSpecificationExecutor<Material> {

    // True si ya existe un material con ese nombre dentro de la misma categoria
    // (RF-002: sin duplicados). El "_" fuerza la navegacion a Categoria.idCategoria
    boolean existsByNombreAndCategoria_IdCategoria(String nombre, Integer idCategoria);

    // Busca un material por id que siga activo (para CRUD de materiales)
    Optional<Material> findByIdMaterialAndActivoTrue(Integer idMaterial);

    // Lista materiales activos ordenados por nombre (RF-001)
    List<Material> findAllByActivoTrueOrderByNombreAsc();

    // Version paginada de la lista de activos (orden por pagina/sort en Pageable)
    Page<Material> findAllByActivoTrue(Pageable pageable);

    // Reporte de bajo stock (RF-012): activos cuyo stock real esta por debajo del
    // minimo, ordenados del mas critico al menos critico
    @Query("SELECT m FROM Material m WHERE m.activo = true AND m.stock < m.stockMinimo ORDER BY m.stock ASC")
    List<Material> findAllActivosBajoMinimo();

    // El conteo de movimientos para RF-004 ahora vive en MovimientoInventarioRepository
    // (countByMaterial_IdMaterial). Fase 6: se elimino el COUNT nativo que habia aqui.

    // Materiales activos asignados a una zona (RF-015): para validar que no se pueda
    // desactivar una zona que sigue en uso ni cambiar su tipo permitido
    List<Material> findAllByActivoTrueAndZona_IdZona(Integer idZona);

    // Materiales activos asignados a varias zonas (RF-015): conteo por zona en una sola
    // consulta para el listado paginado de zonas (mismo patron que proveedores)
    List<Material> findAllByActivoTrueAndZona_IdZonaIn(Collection<Integer> ids);
}