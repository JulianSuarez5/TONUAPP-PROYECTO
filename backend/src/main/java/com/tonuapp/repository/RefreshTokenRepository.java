package com.tonuapp.repository;

import com.tonuapp.domain.RefreshToken;
import com.tonuapp.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Busca un refresh token valido por su hash (revocado=false); los revocados se ignoran
    Optional<RefreshToken> findByTokenHashAndRevocadoFalse(String tokenHash);

    // Elimina en bloque los refresh tokens de un usuario (para limpieza/desactivacion)
    void deleteByUsuario(Usuario usuario);
}