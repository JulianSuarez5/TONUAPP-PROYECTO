package com.tonuapp.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticacion JWT: lee el header Authorization ("Bearer <token>"), valida la
 * firma/expiración y, si es valido, carga el {@link AuthenticatedUser} en el
 * SecurityContext. Si no hay token o el token es invalido, la peticion pasa sin
 * autenticar (la cadena de seguridad decide si el acceso requiere autenticacion).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    // Por cada request: si trae "Bearer <token>", lo valida y carga el usuario en el
    // SecurityContext; si no hay token o es invalido, deja pasar sin autenticar
    // (la cadena de seguridad decide si hace falta auth para el endpoint)
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validar(token);
                Integer idUsuario = Integer.valueOf(claims.getSubject());
                String rol = claims.get("rol", String.class);
                String correo = claims.get("correo", String.class);

                AuthenticatedUser user = new AuthenticatedUser(idUsuario, correo, rol);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                List.of(new SimpleGrantedAuthority(AuthenticatedUser.authority(rol))));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                log.warn("Token JWT invalido: {}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}