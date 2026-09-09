package com.tonuapp.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/**
 * Pruebas unitarias del manejo centralizado de errores (AGENTS.md section 12/13).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRequestURI(uri);
        return req;
    }

    @Test
    @DisplayName("ApiException se traduce a ApiError con el status y mensaje esperados")
    void handlesApiException() {
        ApiException ex = new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado");
        var response = handler.handleApiException(ex, request("/api/materiales"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).isEqualTo("Material no encontrado");
        assertThat(body.path()).isEqualTo("/api/materiales");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("La respuesta de error tiene siempre la misma estructura")
    void apiErrorStructureIsStable() {
        ApiError body = ApiError.of(400, "Bad Request", "algo", "/path");
        assertThat(body.timestamp()).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        assertThat(body.message()).isEqualTo("algo");
        assertThat(body.path()).isEqualTo("/path");
    }

    @Test
    @DisplayName("Content-Type no soportado devuelve 415, no 500")
    void handlesUnsupportedMediaType() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException("application/xml");
        var response = handler.handleUnsupportedMediaType(ex, request("/api/auth/verificar"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        ApiError body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(415);
        assertThat(body.message()).contains("application/json");
    }
}
