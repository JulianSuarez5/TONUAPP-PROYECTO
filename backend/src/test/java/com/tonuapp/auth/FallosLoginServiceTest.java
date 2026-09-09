package com.tonuapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.config.AuthProperties;
import com.tonuapp.domain.CodigoAcceso;
import com.tonuapp.domain.Usuario;
import com.tonuapp.repository.CodigoAccesoRepository;
import com.tonuapp.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

class FallosLoginServiceTest {

    private CodigoAccesoRepository codigoAccesoRepository;
    private UsuarioRepository usuarioRepository;
    private FallosLoginService service;
    private Usuario usuario;
    private CodigoAcceso codigo;

    @BeforeEach
    void setUp() {
        codigoAccesoRepository = mock(CodigoAccesoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        AuthProperties props = new AuthProperties();
        props.setLoginMaxFailedAttempts(5);
        props.setLoginLockoutMinutes(15);
        service = new FallosLoginService(codigoAccesoRepository, usuarioRepository, props);

        usuario = new Usuario();
        usuario.setIdUsuario(2);
        usuario.setIntentosLoginFallidos(0);

        codigo = new CodigoAcceso();
        codigo.setIdCodigo(10);
        codigo.setIntentosFallidos(0);
    }

    @Test
    @DisplayName("registrarFallo: incrementa intentos del codigo y de la cuenta sin bloquear")
    void acumulaFallos() {
        when(codigoAccesoRepository.findById(10)).thenReturn(Optional.of(codigo));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(usuario));
        when(codigoAccesoRepository.save(any(CodigoAcceso.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        service.registrarFallo(10, 2);

        assertThat(codigo.getIntentosFallidos()).isEqualTo(1);
        assertThat(usuario.getIntentosLoginFallidos()).isEqualTo(1);
        assertThat(usuario.getBloqueoHasta()).isNull();
    }

    @Test
    @DisplayName("registrarFallo: al llegar al umbral activa el bloqueo temporal y resetea el contador")
    void activaBloqueoAlUmbral() {
        usuario.setIntentosLoginFallidos(4);
        when(codigoAccesoRepository.findById(10)).thenReturn(Optional.of(codigo));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(usuario));
        when(codigoAccesoRepository.save(any(CodigoAcceso.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        service.registrarFallo(10, 2);

        assertThat(usuario.getBloqueoHasta()).isNotNull();
        assertThat(usuario.getBloqueoHasta()).isAfter(LocalDateTime.now());
        assertThat(usuario.getIntentosLoginFallidos()).isZero();
    }

    @Test
    @DisplayName("registrarFallo: sin usuario ni codigo no falla (datos ya limpiados)")
    void sinRegistrosNoFalla() {
        when(codigoAccesoRepository.findById(10)).thenReturn(Optional.empty());
        when(usuarioRepository.findById(2)).thenReturn(Optional.empty());

        service.registrarFallo(10, 2);

        verify(codigoAccesoRepository).findById(10);
    }
}