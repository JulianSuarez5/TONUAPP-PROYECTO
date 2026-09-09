package com.tonuapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class WebConfigTest {

    private CorsConfiguration configCon(String origins) {
        CorsConfigurationSource source = sourceCon(origins);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/materiales");
        return source.getCorsConfiguration(request);
    }

    private CorsConfigurationSource sourceCon(String origins) {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "corsOrigins", origins);
        return webConfig.corsConfigurationSource();
    }

    @Test
    @DisplayName("El origen del frontend (Vite 5174) entra en el allowlist CORS")
    void allowsViteOrigin() {
        CorsConfiguration config = configCon("http://localhost:3000,http://localhost:5173,http://localhost:5174");
        assertThat(config.getAllowedOrigins())
                .contains("http://localhost:5173", "http://localhost:5174", "http://localhost:3000");
        assertThat(config.getAllowedMethods())
                .contains("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(config.getAllowedHeaders()).contains("*");
    }

    @Test
    @DisplayName("Un origen no configurado no recibe headers CORS")
    void rejectsUnknownOrigin() {
        CorsConfiguration config = configCon("http://localhost:5173");
        assertThat(config.getAllowedOrigins()).doesNotContain("http://localhost:9999");
    }

    @Test
    @DisplayName("Origins en blanco o con espacios se ignoran (no rompe el permitAll)")
    void ignoresBlankOrigins() {
        CorsConfiguration config = configCon(" , http://localhost:5173 ,");
        assertThat(config.getAllowedOrigins()).containsExactly("http://localhost:5173");
    }
}