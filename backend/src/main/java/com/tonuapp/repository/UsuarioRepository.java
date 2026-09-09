package com.tonuapp.repository;

import com.tonuapp.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    // Busca por correo (para login / listar)
    Optional<Usuario> findByCorreo(String correo);

    // True si el correo ya esta registrado (RF-006: correo unico)
    boolean existsByCorreo(String correo);

    // Busca usuario activo por correo (login: los desactivados no pueden entrar)
    Optional<Usuario> findByCorreoAndActivoTrue(String correo);

    // Cuenta cuantos admins principales hay (protege al admin que se siembra en V1)
    long countByEsAdminPrincipalTrue();
}