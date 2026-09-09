package com.tonuapp.ubicaciones;

import com.tonuapp.shared.PagedResponse;
import com.tonuapp.ubicaciones.dto.LoteRequest;
import com.tonuapp.ubicaciones.dto.LoteResponse;
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
 * Lotes de acopio (RF-015, US-17).
 * - GET: consulta (Administrador y Cliente, RF-009).
 * - POST/PUT/DELETE: solo Administrador.
 */
@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final LoteService loteService;

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    // Lista paginada de lotes activos (Admin y Cliente); idMaterial restringe por material
    @GetMapping
    public PagedResponse<LoteResponse> listar(@RequestParam(required = false) Integer idMaterial,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return loteService.listarActivos(idMaterial, q, page, size);
    }

    // Detalle de un lote activo por id (Admin y Cliente)
    @GetMapping("/{id}")
    public LoteResponse obtener(@PathVariable Integer id) {
        return loteService.obtener(id);
    }

    // Lotes activos de un material, mas recientes primero (Admin y Cliente)
    @GetMapping("/por-material/{idMaterial}")
    public List<LoteResponse> listarPorMaterial(@PathVariable Integer idMaterial) {
        return loteService.listarLotesDeMaterial(idMaterial);
    }

    // Crea un lote (solo Administrador); 409 si el codigo ya existe
    @PreAuthorize("hasRole('Administrador')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoteResponse crear(@Valid @RequestBody LoteRequest request) {
        return loteService.crear(request);
    }

    // Actualiza codigo/cantidad de un lote (solo Administrador); el material es inmutable
    @PreAuthorize("hasRole('Administrador')")
    @PutMapping("/{id}")
    public LoteResponse actualizar(@PathVariable Integer id, @Valid @RequestBody LoteRequest request) {
        return loteService.actualizar(id, request);
    }

    // Soft delete de un lote (solo Administrador); 409 si esta referenciado por movimientos
    @PreAuthorize("hasRole('Administrador')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivar(@PathVariable Integer id) {
        loteService.desactivar(id);
    }
}