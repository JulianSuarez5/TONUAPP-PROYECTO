package com.tonuapp.security;

import com.tonuapp.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Emision y validacion de JWT (access token). La firma usa HS256 con la clave de
 * {@link JwtProperties} (decision D-10). El refresh token es opaco (ver AuthService/
 * RefreshToken) y se persiste hasheado.
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    // Crea el access token HS256 con el id (subject), rol y correo como claims
    public String generarAccessToken(Integer idUsuario, String nombreRol, String correo) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(props.getAccessExpirationMinutes() * 60L);
        return Jwts.builder()
                .subject(String.valueOf(idUsuario))
                .claim("rol", nombreRol)
                .claim("correo", correo)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    // Valida firma y expiracion del token; devuelve los claims o lanza JwtException
    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}