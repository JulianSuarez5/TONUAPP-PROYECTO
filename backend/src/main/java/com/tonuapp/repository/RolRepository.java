package com.tonuapp.repository;

import com.tonuapp.domain.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    // Busca un rol por su nombre (ej. "Administrador", utilizado en el seed y en el login)
    Optional<Rol> findByNombreRol(String nombreRol);
}