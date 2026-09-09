package com.tonuapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tonuapp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties props;

    @BeforeEach
    void setUp() {
        props = new JwtProperties();
        props.setSecret("a".repeat(64)); // 64 bytes >= 256 bits (D-10)
        props.setAccessExpirationMinutes(15);
        props.setRefreshExpirationDays(7);
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("Genera un token con subject y claim rol, y lo puede validar")
    void generatesAndValidatesToken() {
        String token = jwtService.generarAccessToken(7, "Administrador", "admin@tonusco.test");
        Claims claims = jwtService.validar(token);
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("rol", String.class)).isEqualTo("Administrador");
        assertThat(claims.get("correo", String.class)).isEqualTo("admin@tonusco.test");
    }

    @Test
    @DisplayName("Un token firmado con otra clave se rechaza")
    void rejectsForgedToken() {
        JwtProperties other = new JwtProperties();
        other.setSecret("b".repeat(64));
        JwtService otherService = new JwtService(other);
        String token = otherService.generarAccessToken(1, "Cliente", "c@test.com");
        assertThatThrownBy(() -> jwtService.validar(token))
                .isInstanceOf(JwtException.class);
    }
}