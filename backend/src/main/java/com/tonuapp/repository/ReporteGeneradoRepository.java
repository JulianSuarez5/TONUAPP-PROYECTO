package com.tonuapp.repository;

import com.tonuapp.domain.ReporteGenerado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Bitacora de exportaciones de reportes (RF-014), mas reciente primero.
 */
public interface ReporteGeneradoRepository extends JpaRepository<ReporteGenerado, Integer> {

    List<ReporteGenerado> findAllByOrderByFechaGeneracionDesc();
}