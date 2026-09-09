package com.tonuapp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.config.AuthProperties;
import com.tonuapp.config.JwtProperties;
import com.tonuapp.domain.CodigoAcceso;
import com.tonuapp.domain.RefreshToken;
import com.tonuapp.domain.Rol;
import com.tonuapp.domain.Usuario;
import com.tonuapp.mail.EmailSender;
import com.tonuapp.repository.CodigoAccesoRepository;
import com.tonuapp.repository.RefreshTokenRepository;
import com.tonuapp.repository.UsuarioRepository;
import com.tonuapp.security.JwtService;
import com.tonuapp.shared.ApiException;
import com.tonuapp.util.Hasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;

class AuthServiceTest {

    private UsuarioRepository usuarioRepository;
    private CodigoAccesoRepository codigoAccesoRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private EmailSender emailSender;
    private FallosLoginService fallosLoginService;
    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioRepository = mock(UsuarioRepository.class);
        codigoAccesoRepository = mock(CodigoAccesoRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        emailSender = mock(EmailSender.class);
        fallosLoginService = mock(FallosLoginService.class);

        JwtProperties jwtProps = new JwtProperties();
        jwtProps.setSecret("a".repeat(64));
        jwtProps.setAccessExpirationMinutes(15);
        jwtProps.setRefreshExpirationDays(7);

        AuthProperties authProps = new AuthProperties();
        authProps.setCodeCooldownSeconds(60);
        authProps.setCodeExpirationMinutes(10);
        authProps.setMaxFailedAttempts(5);

        JwtService jwtService = new JwtService(jwtProps);
        authService = new AuthService(usuarioRepository, codigoAccesoRepository, refreshTokenRepository,
                jwtService, emailSender, jwtProps, authProps, fallosLoginService);

        Rol rol = new Rol();
        rol.setIdRol(1);
        rol.setNombreRol("Administrador");
        usuario = new Usuario();
        usuario.setIdUsuario(1);
        usuario.setCorreo("admin@tonusco.test");
        usuario.setNombre("Admin");
        usuario.setRol(rol);
        usuario.setActivo(true);
        usuario.setFechaCreacion(LocalDateTime.now());
    }

    private CodigoAcceso codigoActivo(int segundosAtras, String codigoClaro, int intentos) {
        CodigoAcceso c = new CodigoAcceso();
        c.setIdCodigo(10);
        c.setUsuario(usuario);
        c.setCodigoHash(Hasher.sha256Hex(codigoClaro));
        c.setFechaGeneracion(LocalDateTime.now().minusSeconds(segundosAtras));
        c.setFechaExpiracion(LocalDateTime.now().plusMinutes(5));
        c.setUsado(false);
        c.setIntentosFallidos(intentos);
        return c;
    }

    @Test
    @DisplayName("solicitarCodigo: rechaza codigo nuevo dentro del cooldown (D-12)")
    void cooldownRejectsRepeatedRequest() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        CodigoAcceso reciente = codigoActivo(10, "111111", 0);
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(reciente));

        assertThatThrownBy(() -> authService.solicitarCodigo("admin@tonusco.test"))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        verify(codigoAccesoRepository, never()).save(any(CodigoAcceso.class));
    }

    @Test
    @DisplayName("solicitarCodigo: permite codigo nuevo cuando el anterior expiro (fuera de cooldown)")
    void cooldownAllowsAfterExpiration() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        CodigoAcceso expirado = codigoActivo(600, "111111", 0);
        expirado.setFechaExpiracion(LocalDateTime.now().minusMinutes(1));
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(expirado));
        when(codigoAccesoRepository.save(any(CodigoAcceso.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = authService.solicitarCodigo("admin@tonusco.test");

        assertThat(resp.message()).isNotBlank();
        verify(codigoAccesoRepository).save(any(CodigoAcceso.class));
    }

    @Test
    @DisplayName("verify: codigo incorrecto delega el registro del fallo en transaccion propia")
    void verifyWrongCode() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        CodigoAcceso c = codigoActivo(5, "111111", 0);
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(c));

        assertThatThrownBy(() -> authService.verificar("admin@tonusco.test", "999999"))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        // el registro del fallo persiste en REQUIRES_NEW (no se pierde con el rollback)
        verify(fallosLoginService).registrarFallo(10, 1);
    }

    @Test
    @DisplayName("verify: codigo correcto emite access + refresh token y marca el codigo usado")
    void verifyCorrectCodeEmitsTokens() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        CodigoAcceso c = codigoActivo(5, "111111", 0);
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(c));
        when(codigoAccesoRepository.save(any(CodigoAcceso.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = authService.verificar("admin@tonusco.test", "111111");

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.rol()).isEqualTo("Administrador");
        assertThat(c.isUsado()).isTrue();
    }

    @Test
    @DisplayName("verify: bloquea cuando se superaron los intentos fallidos")
    void verifyBlocksAfterTooManyAttempts() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        CodigoAcceso c = codigoActivo(5, "111111", 5);
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(c));

        assertThatThrownBy(() -> authService.verificar("admin@tonusco.test", "111111"))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("verify: cuenta con bloqueo temporal activo responde 429 y no continua el flujo")
    void verifyRejectsWhileAccountLocked() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        usuario.setBloqueoHasta(LocalDateTime.now().plusMinutes(10));
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verificar("admin@tonusco.test", "111111"))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        // no se emite token ni se marca ningun codigo como usado
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(codigoAccesoRepository, never()).save(any(CodigoAcceso.class));
    }

    @Test
    @DisplayName("verify: al llegar al umbral de fallos delega la activacion del bloqueo de la cuenta")
    void verifyWrongCodeDelegatesLockout() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        usuario.setIntentosLoginFallidos(4); // ultimo fallo antes del bloqueo
        CodigoAcceso c = codigoActivo(5, "111111", 1);
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(c));

        assertThatThrownBy(() -> authService.verificar("admin@tonusco.test", "999999"))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // la logica de bloqueo vive en FallosLoginService (transaccion propia); aquí solo
        // se delega y la entidad en memoria no se muta (rollback del tx actual)
        verify(fallosLoginService).registrarFallo(10, 1);
        assertThat(usuario.getBloqueoHasta()).isNull();
        assertThat(usuario.getIntentosLoginFallidos()).isEqualTo(4);
    }

    @Test
    @DisplayName("verify: un login exitoso limpia el contador y el bloqueo pendiente de la cuenta")
    void verifySuccessClearsAccountLockout() {
        when(usuarioRepository.findByCorreoAndActivoTrue("admin@tonusco.test")).thenReturn(Optional.of(usuario));
        usuario.setIntentosLoginFallidos(3);
        usuario.setBloqueoHasta(null); // no esta bloqueada en este momento
        CodigoAcceso c = codigoActivo(5, "111111", 0);
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario))
                .thenReturn(Optional.of(c));
        when(codigoAccesoRepository.save(any(CodigoAcceso.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = authService.verificar("admin@tonusco.test", "111111");

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(usuario.getIntentosLoginFallidos()).isZero();
        assertThat(usuario.getBloqueoHasta()).isNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("refresh: revoca el token viejo (rotacion) y emite tokens nuevos")
    void refreshRotatesToken() {
        when(refreshTokenRepository.findByTokenHashAndRevocadoFalse(anyString())).thenAnswer(inv -> {
            RefreshToken rt = new RefreshToken();
            rt.setId(1L);
            rt.setUsuario(usuario);
            rt.setExpiracion(LocalDateTime.now().plusDays(6));
            rt.setRevocado(false);
            return Optional.of(rt);
        });
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(any()))
                .thenReturn(Optional.empty());

        var resp = authService.renovarToken("un-refresh-token");

        assertThat(resp.accessToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotBlank();
        assertThat(resp.refreshToken()).isNotEqualTo("un-refresh-token");
    }
}