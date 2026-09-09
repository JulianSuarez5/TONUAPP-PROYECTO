package com.tonuapp.usuarios;

import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.Rol;
import com.tonuapp.domain.Usuario;
import com.tonuapp.repository.RolRepository;
import com.tonuapp.repository.UsuarioRepository;
import com.tonuapp.security.AuthenticatedUser;
import com.tonuapp.security.SecurityUtils;
import com.tonuapp.shared.ApiException;
import com.tonuapp.usuarios.dto.UsuarioRequest;
import com.tonuapp.usuarios.dto.UsuarioResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * CRUD de usuarios con auditoria (RF-006, RF-009, US-01 a US-04).
 * Reglas:
 *  - correo unico;
 *  - no se desactiva (elimina) la cuenta del admin principal;
 *  - un usuario no puede cambiar su propio rol (RF-009);
 *  - soft delete: "eliminar" = activo = false (D-04).
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;

    public UsuarioService(UsuarioRepository usuarioRepository, RolRepository rolRepository) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
    }

    // Lista todos los usuarios (directorio interno, solo Admin)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioMapper::toResponse)
                .toList();
    }

    // Devuelve un usuario activo por id, o 404
    public UsuarioResponse obtener(Integer id) {
        return UsuarioMapper.toResponse(obtenerActivo(id));
    }

    // Crea un usuario con rol; 409 si el correo ya existe (RF-006: correo unico)
    @Auditable(entidad = "usuarios", operacion = "CREATE")
    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        String correo = request.correo().toLowerCase().trim();
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo.");
        }
        Rol rol = findRol(request.idRol());

        Usuario u = new Usuario();
        u.setNombre(request.nombre().trim());
        u.setCorreo(correo);
        u.setRol(rol);
        u.setActivo(true);
        u.setEsAdminPrincipal(false);
        u.setFechaCreacion(LocalDateTime.now());
        u.setFechaActualizacion(null);
        return UsuarioMapper.toResponse(usuarioRepository.save(u));
    }

    // Actualiza datos basicos de un usuario (nombre/correo/rol), sin permitir duplicados
    @Auditable(entidad = "usuarios", operacion = "UPDATE")
    @Transactional
    public UsuarioResponse actualizar(Integer id, UsuarioRequest request) {
        Usuario u = obtenerActivo(id);
        String correo = request.correo().toLowerCase().trim();
        if (usuarioRepository.existsByCorreo(correo) && !u.getCorreo().equalsIgnoreCase(correo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un usuario con ese correo.");
        }
        u.setNombre(request.nombre().trim());
        u.setCorreo(correo);
        u.setRol(findRol(request.idRol()));
        u.setFechaActualizacion(LocalDateTime.now());
        return UsuarioMapper.toResponse(usuarioRepository.save(u));
    }

    // Cambia solo el rol de un usuario; nadie puede cambiar su propio rol (RF-009)
    @Auditable(entidad = "usuarios", operacion = "UPDATE")
    @Transactional
    public UsuarioResponse cambiarRol(Integer id, Integer idRol) {
        Usuario u = obtenerActivo(id);
        usrPropioNoCambiaRol(id);
        u.setRol(findRol(idRol));
        u.setFechaActualizacion(LocalDateTime.now());
        return UsuarioMapper.toResponse(usuarioRepository.save(u));
    }

    // Soft delete (D-04). El admin principal no se puede desactivar (RF-006)
    @Auditable(entidad = "usuarios", operacion = "DELETE")
    @Transactional
    public void desactivar(Integer id) {
        Usuario u = obtenerActivo(id);
        if (u.isEsAdminPrincipal()) {
            throw new ApiException(HttpStatus.CONFLICT, "No se puede eliminar la cuenta del administrador principal.");
        }
        u.setActivo(false);
        u.setFechaActualizacion(LocalDateTime.now());
        usuarioRepository.save(u);
    }

    // Helper: carga un usuario activo o lanza 404
    private Usuario obtenerActivo(Integer id) {
        return usuarioRepository.findById(id)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuario no encontrado."));
    }

    // Helper: carga un rol o lanza 404
    private Rol findRol(Integer idRol) {
        return rolRepository.findById(idRol)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rol no encontrado."));
    }

    // Bloquea que un usuario se cambie el rol a si mismo (RF-009)
    private void usrPropioNoCambiaRol(Integer targetId) {
        Optional<AuthenticatedUser> current = SecurityUtils.currentUser();
        current.ifPresent(u -> {
            if (u.idUsuario().equals(targetId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, "Un usuario no puede cambiar su propio rol (RF-009).");
            }
        });
    }
}