package com.tonuapp.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    @DisplayName("Una clave corta falla la validacion (D-10)")
    void rejectsShortSecret() {
        JwtProperties props = new JwtProperties();
        props.setSecret("corto"); // < 32 bytes
        assertThatThrownBy(props::validate).hasMessageContaining("256 bits");
    }

    @Test
    @DisplayName("Un secret en blanco falla la validacion (D-10)")
    void rejectsBlankSecret() {
        JwtProperties props = new JwtProperties();
        assertThatThrownBy(props::validate).hasMessageContaining("no esta configurada");
    }
}