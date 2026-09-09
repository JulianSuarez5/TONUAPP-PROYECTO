package com.tonuapp.repository;

import com.tonuapp.domain.UnidadMedida;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnidadMedidaRepository extends JpaRepository<UnidadMedida, Integer> {

    // Lista todas las unidades ordenadas por nombre (dropdown del frontend)
    List<UnidadMedida> findAllByOrderByNombreAsc();
}