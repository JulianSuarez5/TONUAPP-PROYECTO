package com.tonuapp.repository;

import com.tonuapp.domain.CodigoAcceso;
import com.tonuapp.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodigoAccesoRepository extends JpaRepository<CodigoAcceso, Integer> {

    // Ultimo codigo SIN usar del usuario (para validar en login; usado=false)
    Optional<CodigoAcceso> findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(Usuario usuario);

    // Historial de codigos de un usuario, del mas reciente al mas antiguo
    List<CodigoAcceso> findByUsuarioOrderByFechaGeneracionDesc(Usuario usuario);
}