package com.tonuapp.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;

class HasherTest {

    @Test
    @DisplayName("sha256Hex devuelve 64 caracteres hex y es estable")
    void sha256HexIsStable() {
        String h1 = Hasher.sha256Hex("123456");
        String h2 = Hasher.sha256Hex("123456");
        assertThat(h1).hasSize(64).isEqualTo(h2);
        assertThat(h1).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("sha256Hex de dos valores distintos no colisiona")
    void sha256HexDiffers() {
        assertThat(Hasher.sha256Hex("123456")).isNotEqualTo(Hasher.sha256Hex("654321"));
    }

    @Test
    @DisplayName("generarCodigoNumerico devuelve 6 digitos")
    void generates6DigitCode() {
        assertThat(Hasher.generarCodigoNumerico()).matches("^\\d{6}$");
    }

    @Test
    @DisplayName("generarTokenAleatorio devuelve 64 caracteres hex")
    void generatesRandomToken() {
        assertThat(Hasher.generarTokenAleatorio()).hasSize(64).matches("[0-9a-f]{64}");
    }
}