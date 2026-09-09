package com.tonuapp.auth;

import com.tonuapp.config.AuthProperties;
import com.tonuapp.domain.CodigoAcceso;
import com.tonuapp.domain.Usuario;
import com.tonuapp.repository.CodigoAccesoRepository;
import com.tonuapp.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persistencia de los intentos de verificacion fallidos (RF-008 / D-22) en una
 * transaccion PROPIA (REQUIRES_NEW), el mismo patron que AuditService (D-06):
 * la operacion de login fallido termina en excepcion y el @Transactional padre hara
 * rollback; si el incremento del contador viviera en esa transaccion, se perderia y el
 * bloqueo por cuenta nunca se activaria (detectado en el E2E de la Fase 10; documentado
 * en D-22).
 */
@Service
public class FallosLoginService {

    private final CodigoAccesoRepository codigoAccesoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuthProperties authProperties;

    public FallosLoginService(CodigoAccesoRepository codigoAccesoRepository,
                              UsuarioRepository usuarioRepository,
                              AuthProperties authProperties) {
        this.codigoAccesoRepository = codigoAccesoRepository;
        this.usuarioRepository = usuarioRepository;
        this.authProperties = authProperties;
    }

    /**
     * Registra UNA verificacion fallida en dos niveles, leyendo el estado FRESCO de la
     * BD (nueva transaccion):
     *  - codigo: incrementa intentos_fallidos.
     *  - cuenta: incrementa intentos_login_fallidos y, al llegar al umbral, activa el
     *    bloqueo temporal (bloqueo_hasta) reiniciando el contador.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(Integer idCodigo, Integer idUsuario) {
        CodigoAcceso codigo = codigoAccesoRepository.findById(idCodigo).orElse(null);
        if (codigo != null) {
            codigo.setIntentosFallidos(codigo.getIntentosFallidos() + 1);
            codigoAccesoRepository.save(codigo);
        }

        usuarioRepository.findById(idUsuario).ifPresent(usuario -> {
            int nuevosFallos = usuario.getIntentosLoginFallidos() + 1;
            if (nuevosFallos >= authProperties.getLoginMaxFailedAttempts()) {
                usuario.setBloqueoHasta(LocalDateTime.now().plusMinutes(authProperties.getLoginLockoutMinutes()));
                usuario.setIntentosLoginFallidos(0);
            } else {
                usuario.setIntentosLoginFallidos(nuevosFallos);
            }
            usuarioRepository.save(usuario);
        });
    }
}