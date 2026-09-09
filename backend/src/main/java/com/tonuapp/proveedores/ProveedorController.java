package com.tonuapp.proveedores;

import com.tonuapp.proveedores.dto.MaterialProveedorRequest;
import com.tonuapp.proveedores.dto.MaterialProveedorResponse;
import com.tonuapp.proveedores.dto.ProveedorRequest;
import com.tonuapp.proveedores.dto.ProveedorResponse;
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
 * Proveedores y su relacion N:M con materiales (RF-010, D-01/D-14).
 * - GET: consulta (Administrador y Cliente, RF-009).
 * - POST/PUT/DELETE (proveedores y asociaciones): solo Administrador.
 * Incluye un endpoint cruzado para consultar los proveedores de un material, ya que
 * pertenece a este modulo.
 */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    public ProveedorController(ProveedorService proveedorService) {
        this.proveedorService = proveedorService;
    }

    // Lista paginada de proveedores activos con su cantidad de materiales (Admin y Cliente);
    // q opcional filtra por nombre o NIT (contiene, sin distinguir mayusculas)
    @GetMapping
    public PagedResponse<ProveedorResponse> listar(@RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return proveedorService.listarActivos(q, page, size);
    }

    // Detalle de un proveedor activo por id (Admin y Cliente)
    @GetMapping("/{id}")
    public ProveedorResponse obtener(@PathVariable Integer id) {
        return proveedorService.obtener(id);
    }

    // Materiales asociados a un proveedor (Admin y Cliente)
    @GetMapping("/{id}/materiales")
    public List<MaterialProveedorResponse> listarMateriales(@PathVariable Integer id) {
        return proveedorService.listarMaterialesDeProveedor(id);
    }

    // Crea un proveedor (solo Administrador); 409 si el NIT ya existe
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProveedorResponse crear(@Valid @RequestBody ProveedorRequest request) {
        return proveedorService.crear(request);
    }

    // Actualiza un proveedor (solo Administrador)
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}")
    public ProveedorResponse actualizar(@PathVariable Integer id, @Valid @RequestBody ProveedorRequest request) {
        return proveedorService.actualizar(id, request);
    }

    // Soft delete de un proveedor (solo Administrador); 409 si tiene materiales (RF-010)
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) {
        proveedorService.desactivar(id);
    }

    // Asocia un material a un proveedor (solo Administrador, N:M); 409 si ya estaba asociado
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping("/{id}/materiales")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialProveedorResponse asociarMaterial(@PathVariable Integer id,
                                                     @Valid @RequestBody MaterialProveedorRequest request) {
        return proveedorService.asociarMaterial(id, request);
    }

    // Marca/desmarca un material como principal de un proveedor (solo Administrador);
    // al marcar principal=true se desmarca el principal anterior de ese material
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}/materiales/{idMaterial}")
    public MaterialProveedorResponse actualizarPrincipal(@PathVariable Integer id,
                                                         @PathVariable Integer idMaterial,
                                                         @RequestParam(required = false) Boolean principal) {
        return proveedorService.actualizarPrincipal(id, idMaterial, Boolean.TRUE.equals(principal));
    }

    // Quita la asociacion material-proveedor (solo Administrador)
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}/materiales/{idMaterial}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desasociarMaterial(@PathVariable Integer id, @PathVariable Integer idMaterial) {
        proveedorService.desasociarMaterial(id, idMaterial);
    }
}