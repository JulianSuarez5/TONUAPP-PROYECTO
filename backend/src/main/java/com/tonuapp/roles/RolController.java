package com.tonuapp.roles;

import com.tonuapp.repository.RolRepository;
import com.tonuapp.roles.dto.RolResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de roles (RF-006). Tabla flexible: agregar roles no requiere rehacer el
 * sistema. Cualquier usuario autenticado puede consultarlos.
 */
@RestController
@RequestMapping("/api/roles")
public class RolController {

    private final RolRepository rolRepository;

    public RolController(RolRepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    // Lista los roles del sistema (tabla flexible: se pueden agregar roles sin rehacer nada)
    @GetMapping
    public List<RolResponse> listar() {
        return rolRepository.findAll().stream()
                .map(r -> new RolResponse(r.getIdRol(), r.getNombreRol(), r.getDescripcion()))
                .toList();
    }
}