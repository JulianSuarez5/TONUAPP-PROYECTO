package com.tonuapp.repository;

import com.tonuapp.domain.ZonaAcopio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ZonaAcopioRepository extends JpaRepository<ZonaAcopio, Integer>,
        JpaSpecificationExecutor<ZonaAcopio> {

    Optional<ZonaAcopio> findByIdZonaAndActivoTrue(Integer idZona);

    boolean existsByNombreZonaIgnoreCase(String nombreZona);

    boolean existsByNombreZonaIgnoreCaseAndIdZonaNot(String nombreZona, Integer idZona);
}