package com.tonuapp.materiales;

import com.tonuapp.materiales.dto.MaterialRequest;
import com.tonuapp.materiales.dto.MaterialResponse;
import com.tonuapp.proveedores.ProveedorService;
import com.tonuapp.shared.PagedResponse;
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
 * CRUD y busqueda de materiales (RF-001 a RF-004).
 * - GET: consulta (Administrador y Cliente, RF-001/RF-009).
 * - POST/PUT/DELETE: solo Administrador (RF-002/RF-004).
 */
@RestController
@RequestMapping("/api/materiales")
public class MaterialController {

    private final MaterialService materialService;
    private final ProveedorService proveedorService;

    public MaterialController(MaterialService materialService, ProveedorService proveedorService) {
        this.materialService = materialService;
        this.proveedorService = proveedorService;
    }

    // Lista paginada de materiales activos en orden alfabetico (Admin y Cliente, RF-001)
    @GetMapping
    public PagedResponse<MaterialResponse> listar(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return materialService.listarActivos(page, size);
    }

    // Busca materiales por nombre y/o categoria (exige al menos un filtro, RF-003), paginado
    @GetMapping("/buscar")
    public PagedResponse<MaterialResponse> buscar(@RequestParam(required = false) String nombre,
                                                  @RequestParam(required = false) Integer categoria,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return materialService.buscar(nombre, categoria, page, size);
    }

    // Detalle de un material activo por id (Admin y Cliente)
    @GetMapping("/{id}")
    public MaterialResponse obtener(@PathVariable Integer id) {
        return materialService.obtener(id);
    }

    // Proveedores asociados a un material (N:M, Admin y Cliente). Vive aqui porque la
    // ruta es recursiva a /api/materiales/{id}/proveedores
    @GetMapping("/{id}/proveedores")
    public List<com.tonuapp.proveedores.dto.MaterialProveedorResponse> listarProveedores(@PathVariable Integer id) {
        return proveedorService.listarProveedoresDeMaterial(id);
    }

    // Crea un material (solo Administrador, RF-002). 409 si hay duplicado nombre+categoria
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialResponse crear(@Valid @RequestBody MaterialRequest request) {
        return materialService.crear(request);
    }

    // Actualiza un material (solo Administrador)
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}")
    public MaterialResponse actualizar(@PathVariable Integer id, @Valid @RequestBody MaterialRequest request) {
        return materialService.actualizar(id, request);
    }

    // Soft delete de un material (solo Administrador); 409 si tiene movimientos (RF-004)
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) {
        materialService.desactivar(id);
    }
}