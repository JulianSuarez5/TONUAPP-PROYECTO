package com.tonuapp.auditoria;

import com.tonuapp.auditoria.dto.AuditoriaResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Consulta del log de auditoria (Fase 10 / D-22). Exclusivo del rol Administrador:
 * el log revela QUIEN hizo QUE y los valores antes/despues de cada operacion.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    // Filtros opcionales: entidad (ej. materiales, movimientos_inventario), operacion
    // (CREATE/UPDATE/DELETE/MOVEMENT/ADJUST), idUsuario, rango de fechas (desde/hasta)
    // y size (default 100, 1..500, D-23): la lista siempre llega acotada
    @PreAuthorize("hasRole('Administrador')")
    @GetMapping
    public List<AuditoriaResponse> consultar(
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String operacion,
            @RequestParam(required = false) Integer idUsuario,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer size) {
        return auditoriaService.consultar(entidad, operacion, idUsuario, desde, hasta, size);
    }
}