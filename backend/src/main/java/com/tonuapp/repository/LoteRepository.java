package com.tonuapp.repository;

import com.tonuapp.domain.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface LoteRepository extends JpaRepository<Lote, Integer>, JpaSpecificationExecutor<Lote> {

    Optional<Lote> findByIdLoteAndActivoTrue(Integer idLote);

    boolean existsByCodigoLote(String codigoLote);

    boolean existsByCodigoLoteAndIdLoteNot(String codigoLote, Integer idLote);

    List<Lote> findAllByActivoTrueAndMaterial_IdMaterialOrderByFechaIngresoDesc(Integer idMaterial);
}