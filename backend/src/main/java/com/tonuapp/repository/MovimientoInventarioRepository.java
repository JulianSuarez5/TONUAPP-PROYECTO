package com.tonuapp.repository;

import com.tonuapp.domain.MovimientoInventario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer>,
        JpaSpecificationExecutor<MovimientoInventario> {

    // Cuenta TODOS los movimientos de un material (activos y anulados): usado por
    // RF-004 (un material con movimientos no se elimina). Reemplaza al COUNT nativo
    // que vivia en MaterialRepository (TODO Fase 6 resuelto)
    long countByMaterial_IdMaterial(Integer idMaterial);

    // Historial de movimientos de un material, mas reciente primero
    List<MovimientoInventario> findAllByMaterial_IdMaterialOrderByFechaMovimientoDesc(Integer idMaterial);

    // Historial global de movimientos, mas reciente primero
    List<MovimientoInventario> findAllByOrderByFechaMovimientoDesc();

    // Reporte de movimientos (RF-014): movimientos dentro de un rango de fechas,
    // mas reciente primero (incluye anulados para la trazabilidad completa; el
    // reporte resumen los excluye porque el efecto fue revertido)
    List<MovimientoInventario> findAllByFechaMovimientoBetweenOrderByFechaMovimientoDesc(LocalDateTime desde, LocalDateTime hasta);

    // Bloqueos de soft-delete (RF-015): zona/lote con movimientos referenciados no se puede
    // desactivar, porque el historial pierde trazabilidad del nombre
    long countByIdLote(Integer idLote);

    long countByIdZona(Integer idZona);
}