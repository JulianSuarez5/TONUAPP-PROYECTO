package com.tonuapp.usuarios;

import com.tonuapp.usuarios.dto.UpdateRolRequest;
import com.tonuapp.usuarios.dto.UsuarioRequest;
import com.tonuapp.usuarios.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de usuarios (RF-006/RF-009). Toda la gestion y la consulta del directorio de
 * usuarios es exclusiva del rol Administrador; un Cliente no debe poder listar las
 * cuentas internas del sistema (solo consulta inventario, RF-009).
 */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    // Lista el directorio de usuarios (solo Admin)
    @PreAuthorize("hasRole('Administrador')")
    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarioService.listar();
    }

    // Detalle de un usuario (solo Admin)
    @PreAuthorize("hasRole('Administrador')")
    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Integer id) {
        return usuarioService.obtener(id);
    }

    // Crea un usuario (solo Admin); 409 si el correo ya existe
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request) {
        return usuarioService.crear(request);
    }

    // Actualiza un usuario (solo Admin)
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}")
    public UsuarioResponse actualizar(@PathVariable Integer id, @Valid @RequestBody UsuarioRequest request) {
        return usuarioService.actualizar(id, request);
    }

    // Cambia el rol de un usuario (solo Admin; nadie cambia su propio rol)
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}/rol")
    public UsuarioResponse cambiarRol(@PathVariable Integer id, @Valid @RequestBody UpdateRolRequest request) {
        return usuarioService.cambiarRol(id, request.idRol());
    }

    // Soft delete de un usuario (solo Admin); el admin principal no se borra
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) {
        usuarioService.desactivar(id);
    }
}