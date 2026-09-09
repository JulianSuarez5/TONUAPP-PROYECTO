package com.tonuapp.security;

import com.tonuapp.shared.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracion de seguridad (Fase 3). API stateless (JWT), sin sesiones HTTP.
 * Reglas:
 *  - Publicos: solicitar-codigo, verificar, renovar-token y cerrar-sesion
 *    (POST, van por body, no header).
 *  - El resto de /api/** exige autenticacion.
 *  - La autorizacion fina (Administrador vs Cliente) se declara ademas a nivel de
 *    metodo con @PreAuthorize (RF-009).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * Devuelve 401 (con cuerpo ApiError) cuando se accede a un recurso protegido sin
     * autenticacion valida. El 403 queda reservado a la autorizacion (AccessDenied),
     * distinguiendo correctamente ambos casos (AGENTS.md section 12).
     */
    private org.springframework.security.web.AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            ApiError body = ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized",
                    "Debe autenticarse para acceder a este recurso.", request.getRequestURI());
            response.getWriter().write(objectMapper.writeValueAsString(body));
        };
    }

    // Cadena de seguridad: sin sesiones HTTP (stateless), auth endpoints publicos y el
    // resto de /api/** exige token; la autorizacion fina va por @PreAuthorize en cada metodo
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                // Endurecimiento HTTP (Fase 10 / D-22): la API solo sirve JSON/binario,
                // por eso CSP sin contenido HTML ni scripts, y sin store en cache
                .contentTypeOptions(Customizer.withDefaults())
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(
                        org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'"))
                .cacheControl(Customizer.withDefaults()))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(authenticationEntryPoint()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/solicitar-codigo", "/api/auth/verificar",
                        "/api/auth/renovar-token", "/api/auth/cerrar-sesion").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}