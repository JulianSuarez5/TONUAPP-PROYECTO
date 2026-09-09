package com.tonuapp.repository;

import com.tonuapp.domain.AjusteInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Ajustes/conciliaciones (RF-011). findByMovimiento_IdMovimiento permite anular el
 * movimiento 'ajuste' revirtiendo el stock a cantidad_anterior (D-19).
 */
public interface AjusteInventarioRepository extends JpaRepository<AjusteInventario, Integer> {

    Optional<AjusteInventario> findByMovimiento_IdMovimiento(Integer idMovimiento);

    List<AjusteInventario> findAllByOrderByFechaAjusteDesc();

    // Reporte resumen (RF-013): ajustes dentro de un rango, para sumar el delta
    // cantidadNueva - cantidadAnterior (D-19) y que el efecto neto sea consistente
    List<AjusteInventario> findAllByFechaAjusteBetween(LocalDateTime desde, LocalDateTime hasta);
}