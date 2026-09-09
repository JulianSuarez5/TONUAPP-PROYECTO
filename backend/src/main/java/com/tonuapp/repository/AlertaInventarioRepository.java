package com.tonuapp.repository;

import com.tonuapp.domain.AlertaEstado;
import com.tonuapp.domain.AlertaInventario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Alertas de bajo stock (RF-012, D-05). findFirstBy... permite saber si el material
 * ya tiene una alerta activa (para no duplicar por cada movimiento que siga bajo
 * el minimo).
 */
public interface AlertaInventarioRepository extends JpaRepository<AlertaInventario, Integer> {

    Optional<AlertaInventario> findFirstByMaterial_IdMaterialAndEstadoOrderByFechaGeneradaDesc(Integer idMaterial, AlertaEstado estado);

    List<AlertaInventario> findAllByOrderByFechaGeneradaDesc();

    List<AlertaInventario> findAllByEstadoOrderByFechaGeneradaDesc(AlertaEstado estado);

    List<AlertaInventario> findAllByMaterial_IdMaterialOrderByFechaGeneradaDesc(Integer idMaterial);
}