package com.tonuapp.auth;

import com.tonuapp.auth.dto.AuthResponse;
import com.tonuapp.auth.dto.MessageResponse;
import com.tonuapp.auth.dto.RefreshRequest;
import com.tonuapp.auth.dto.RequestCodeRequest;
import com.tonuapp.auth.dto.VerifyRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticacion (RF-008). solicitar-codigo, verificar, renovar-token y
 * cerrar-sesion son publicos (SecurityConfig); su credencial via en el body, no en el
 * header.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // Endpoint publico: envia el codigo de acceso al correo (o respuesta generica para
    // no revelar correos registrados)
    @PostMapping("/solicitar-codigo")
    public ResponseEntity<MessageResponse> solicitarCodigo(@Valid @RequestBody RequestCodeRequest request) {
        return ResponseEntity.ok(authService.solicitarCodigo(request.correo()));
    }

    // Endpoint publico: valida correo + codigo y devuelve access/refresh token
    @PostMapping("/verificar")
    public ResponseEntity<AuthResponse> verificar(@Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.ok(authService.verificar(request.correo(), request.codigo()));
    }

    // Endpoint publico: renueva el access token con el refresh token (rotacion)
    @PostMapping("/renovar-token")
    public ResponseEntity<AuthResponse> renovarToken(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.renovarToken(request.refreshToken()));
    }

    // Endpoint publico: revoca el refresh token (el access expira solo, D-11)
    @PostMapping("/cerrar-sesion")
    public ResponseEntity<Void> cerrarSesion(@RequestBody(required = false) RefreshRequest request) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            authService.cerrarSesion(request.refreshToken());
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}