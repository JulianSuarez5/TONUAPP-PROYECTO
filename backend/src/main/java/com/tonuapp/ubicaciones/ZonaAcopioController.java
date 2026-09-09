package com.tonuapp.ubicaciones;

import com.tonuapp.materiales.dto.MaterialResponse;
import com.tonuapp.shared.PagedResponse;
import com.tonuapp.ubicaciones.dto.ZonaAcopioRequest;
import com.tonuapp.ubicaciones.dto.ZonaAcopioResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Zonas de acopio y asignacion de materiales (RF-015, US-16).
 * - GET: consulta (Administrador y Cliente, RF-009).
 * - POST/PUT/DELETE y asignaciones: solo Administrador.
 */
@RestController
@RequestMapping("/api/zonas-acopio")
public class ZonaAcopioController {

    private final ZonaAcopioService zonaService;

    public ZonaAcopioController(ZonaAcopioService zonaService) {
        this.zonaService = zonaService;
    }

    // Lista paginada de zonas activas (Admin y Cliente); q filtra por nombre o tipo
    @GetMapping
    public PagedResponse<ZonaAcopioResponse> listar(@RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return zonaService.listarActivas(q, page, size);
    }

    // Detalle de una zona activa por id (Admin y Cliente)
    @GetMapping("/{id}")
    public ZonaAcopioResponse obtener(@PathVariable Integer id) {
        return zonaService.obtener(id);
    }

    // Materiales activos asignados a la zona (Admin y Cliente): indicador de ubicacion RF-015
    @GetMapping("/{id}/materiales")
    public List<MaterialResponse> listarMateriales(@PathVariable Integer id) {
        return zonaService.listarMaterialesDeZona(id);
    }

    // Crea una zona (solo Administrador); 409 si el nombre ya existe
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZonaAcopioResponse crear(@Valid @RequestBody ZonaAcopioRequest request) {
        return zonaService.crear(request);
    }

    // Actualiza una zona (solo Administrador); 409 si el tipo permitido deja incompatibles
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}")
    public ZonaAcopioResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ZonaAcopioRequest request) {
        return zonaService.actualizar(id, request);
    }

    // Soft delete de una zona (solo Administrador); 409 si tiene materiales o movimientos
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) {
        zonaService.desactivar(id);
    }

    // Asigna un material a la zona (solo Administrador); 409 si el material es incompatible
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping("/{id}/materiales/{idMaterial}")
    @ResponseStatus(HttpStatus.OK)
    public MaterialResponse asignarMaterial(@PathVariable Integer id, @PathVariable Integer idMaterial) {
        return zonaService.asignarMaterial(id, idMaterial);
    }

    // Desasigna un material de la zona (solo Administrador)
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}/materiales/{idMaterial}")
    @ResponseStatus(HttpStatus.OK)
    public MaterialResponse desasignarMaterial(@PathVariable Integer id, @PathVariable Integer idMaterial) {
        return zonaService.desasignarMaterial(id, idMaterial);
    }
}