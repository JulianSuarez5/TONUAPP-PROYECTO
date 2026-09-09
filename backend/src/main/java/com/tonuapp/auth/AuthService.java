package com.tonuapp.auth;

import com.tonuapp.auth.dto.AuthResponse;
import com.tonuapp.auth.dto.MessageResponse;
import com.tonuapp.config.AuthProperties;
import com.tonuapp.config.JwtProperties;
import com.tonuapp.domain.CodigoAcceso;
import com.tonuapp.domain.RefreshToken;
import com.tonuapp.domain.Usuario;
import com.tonuapp.mail.EmailSender;
import com.tonuapp.repository.CodigoAccesoRepository;
import com.tonuapp.repository.RefreshTokenRepository;
import com.tonuapp.repository.UsuarioRepository;
import com.tonuapp.security.JwtService;
import com.tonuapp.shared.ApiException;
import com.tonuapp.util.Hasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Optional;

/**
 * Flujo de autenticacion por correo + codigo temporal (RF-008 / D-07).
 * - solicitarCodigo: genera codigo aleatorio, lo hashea (SHA-256 hex) y lo guarda;
 *   aplica cooldown minimo por correo (D-12). "Envia" via EmailSender.
 * - verificar: valida codigo (expiracion, un solo uso, intentos fallidos) y emite
 *   access + refresh token (el refresh se persiste hasheado, revocable en cerrarSesion, D-11).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final CodigoAccesoRepository codigoAccesoRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final EmailSender emailSender;
    private final JwtProperties jwtProperties;
    private final AuthProperties authProperties;
    private final FallosLoginService fallosLoginService;

    public AuthService(UsuarioRepository usuarioRepository,
                       CodigoAccesoRepository codigoAccesoRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtService jwtService,
                       EmailSender emailSender,
                       JwtProperties jwtProperties,
                       AuthProperties authProperties,
                       FallosLoginService fallosLoginService) {
        this.usuarioRepository = usuarioRepository;
        this.codigoAccesoRepository = codigoAccesoRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.emailSender = emailSender;
        this.jwtProperties = jwtProperties;
        this.authProperties = authProperties;
        this.fallosLoginService = fallosLoginService;
    }

    /**
     * Solicita un codigo de acceso. Si el correo no existe, responde igual para no
     * revelar que correos estan registrados (evita enumeracion de usuarios).
     * Aplica cooldown por correo (D-12).
     */
    @Transactional
    public MessageResponse solicitarCodigo(String correo) {
        Optional<Usuario> opt = usuarioRepository.findByCorreoAndActivoTrue(correo.toLowerCase().trim());
        if (opt.isEmpty()) {
            log.info("Solicitud de codigo para correo no registrado: {}", correo);
            return new MessageResponse("Si el correo esta registrado, recibira un codigo de acceso.");
        }

        Usuario usuario = opt.get();
        checkCooldown(usuario);

        String codigoClaro = Hasher.generarCodigoNumerico();
        CodigoAcceso codigo = new CodigoAcceso();
        codigo.setUsuario(usuario);
        codigo.setCodigoHash(Hasher.sha256Hex(codigoClaro));
        codigo.setFechaGeneracion(LocalDateTime.now());
        codigo.setFechaExpiracion(LocalDateTime.now().plusMinutes(authProperties.getCodeExpirationMinutes()));
        codigo.setUsado(false);
        codigo.setIntentosFallidos(0);
        codigoAccesoRepository.save(codigo);

        emailSender.sendAccessCode(usuario.getCorreo(), codigoClaro);
        return new MessageResponse("Se envio un codigo de acceso a su correo.");
    }

    /** Valida el codigo y, si es correcto, emite los tokens. */
    @Transactional
    public AuthResponse verificar(String correo, String codigo) {
        String correoNorm = correo.toLowerCase().trim();
        Usuario usuario = usuarioRepository.findByCorreoAndActivoTrue(correoNorm)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Correo o codigo incorrectos"));

        // Bloqueo temporal por cuenta (RF-008 / D-22)
        if (usuario.getBloqueoHasta() != null && usuario.getBloqueoHasta().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Cuenta bloqueada temporalmente por intentos fallidos. Intente mas tarde.");
        }

        CodigoAcceso registro = codigoAccesoRepository
                .findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Correo o codigo incorrectos"));

        if (registro.getFechaExpiracion().isBefore(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "El codigo ha expirado. Solicite uno nuevo.");
        }

        if (registro.getIntentosFallidos() >= authProperties.getMaxFailedAttempts()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Demasiados intentos fallidos. Solicite un nuevo codigo.");
        }

        if (!registro.getCodigoHash().equals(Hasher.sha256Hex(codigo))) {
            // El contador (codigo + cuenta) se persiste en transaccion propia
            // (REQUIRES_NEW): esta excepcion hara rollback de la transaccion actual,
            // por lo que un save normal aqui nunca dejaria registrado el fallo (D-22)
            fallosLoginService.registrarFallo(registro.getIdCodigo(), usuario.getIdUsuario());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Codigo incorrecto.");
        }

        registro.setUsado(true);
        codigoAccesoRepository.save(registro);
        // Login exitoso: se limpia cualquier bloqueo pendiente de la cuenta (D-22)
        usuario.setIntentosLoginFallidos(0);
        usuario.setBloqueoHasta(null);
        usuarioRepository.save(usuario);

        return emitTokens(usuario);
    }

    /** Renueva los tokens usando un refresh token valido (rotacion). */
    @Transactional
    public AuthResponse renovarToken(String refreshTokenRaw) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevocadoFalse(Hasher.sha256Hex(refreshTokenRaw))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token invalido o revocado"));

        if (stored.getExpiracion().isBefore(LocalDateTime.now())) {
            stored.setRevocado(true);
            refreshTokenRepository.save(stored);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token expirado. Inicie sesion nuevamente.");
        }

        Usuario usuario = stored.getUsuario();
        if (!usuario.isActivo()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Usuario inactivo");
        }

        stored.setRevocado(true); // rotacion: el anterior queda revocado
        refreshTokenRepository.save(stored);
        return emitTokens(usuario);
    }

    /** Revoca el refresh token (el access token corto expira solo, D-11). */
    @Transactional
    public void cerrarSesion(String refreshTokenRaw) {
        refreshTokenRepository.findByTokenHashAndRevocadoFalse(Hasher.sha256Hex(refreshTokenRaw))
                .ifPresent(stored -> {
                    stored.setRevocado(true);
                    refreshTokenRepository.save(stored);
                });
    }

    // Si el ultimo codigo del usuario sigue "fresco", bloquea por cooldown (D-12) y pide esperar
    private void checkCooldown(Usuario usuario) {
        codigoAccesoRepository.findTopByUsuarioAndUsadoFalseOrderByFechaGeneracionDesc(usuario)
                .filter(c -> !c.getFechaExpiracion().isBefore(LocalDateTime.now()))
                .filter(c -> c.getFechaGeneracion().plusSeconds(authProperties.getCodeCooldownSeconds())
                        .isAfter(LocalDateTime.now()))
                .ifPresent(c -> {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                            "Debe esperar antes de solicitar otro codigo para este correo.");
                });
    }

    // Genera access token (JWT firmado) y un refresh token opaco aleatorio que se guarda
    // hasheado con expiracion propia; devuelve ambos al cliente
    private AuthResponse emitTokens(Usuario usuario) {
        String access = jwtService.generarAccessToken(usuario.getIdUsuario(), usuario.getRol().getNombreRol(), usuario.getCorreo());

        String refreshRaw = Hasher.generarTokenAleatorio();
        RefreshToken rt = new RefreshToken();
        rt.setUsuario(usuario);
        rt.setTokenHash(Hasher.sha256Hex(refreshRaw));
        rt.setFechaCreacion(LocalDateTime.now());
        rt.setExpiracion(LocalDateTime.now().plusDays(jwtProperties.getRefreshExpirationDays()));
        rt.setRevocado(false);
        refreshTokenRepository.save(rt);

        Instant accessExp = Instant.now().plusSeconds(jwtProperties.getAccessExpirationMinutes() * 60L);
        Instant refreshExp = Instant.now().plusSeconds(jwtProperties.getRefreshExpirationDays() * 24L * 3600L);
        return new AuthResponse(
                access, refreshRaw, accessExp, refreshExp,
                usuario.getIdUsuario(), usuario.getCorreo(), usuario.getRol().getNombreRol());
    }
}