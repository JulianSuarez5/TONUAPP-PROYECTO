package com.tonuapp.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Configuracion CORS para que el frontend (React) consuma la API. Los origenes se leen
 * de la propiedad tonuapp.cors.origins (variable TONUAPP_CORS_ORIGINS, lista separada
 * por comas) y NO se hardcodean (Fase 10 / D-22).
 *
 * Se expone como {@link CorsConfigurationSource} (no como WebMvcConfigurer) para que la
 * cadena de Spring Security (SecurityConfig) la aplique via .cors(), ANTES de la regla
 * anyRequest().authenticated(). Un WebMvcConfigurer actua en Spring MVC, demasiado tarde:
 * el preflight OPTIONS de un frontend en otro puerto (ej. Vite 5174) era rechazado con 401
 * sin cabeceras CORS y el login nunca completaba la peticion (bug corregido en Fase 11).
 */
@Configuration
public class WebConfig {

    @Value("${tonuapp.cors.origins:http://localhost:3000,http://localhost:5173}")
    private String corsOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        Arrays.stream(splitOrigins()).forEach(config::addAllowedOrigin);
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("*");
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private String[] splitOrigins() {
        return Arrays.stream(corsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}