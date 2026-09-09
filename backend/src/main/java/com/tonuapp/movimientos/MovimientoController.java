package com.tonuapp.movimientos;

import com.tonuapp.movimientos.dto.AjusteRequest;
import com.tonuapp.movimientos.dto.AjusteResponse;
import com.tonuapp.movimientos.dto.MovimientoRequest;
import com.tonuapp.movimientos.dto.MovimientoResponse;
import com.tonuapp.shared.PagedResponse;
import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.TipoMovimiento;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de movimientos de inventario. Todo el modulo es de uso interno (solo
 * Administrador): registrar entradas/salidas/ajustes y consultar el historico.
 * El cliente ve el stock en tiempo real por /api/materiales (RF-001), no el detalle
 * operativo de movimientos.
 */
@RestController
@RequestMapping("/api/movimientos")
@PreAuthorize("hasRole('Administrador')")
public class MovimientoController {

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    // Historico paginado de movimientos (mas reciente primero); filtros opcionales
    // por material, tipo, estado y rango de fechas (RF-004). Solo Administrador
    @GetMapping
    public PagedResponse<MovimientoResponse> listar(
            @RequestParam(required = false) Integer idMaterial,
            @RequestParam(required = false) TipoMovimiento tipo,
            @RequestParam(required = false) EstadoMovimiento estado,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return movimientoService.listar(idMaterial, tipo, estado, desde, hasta, page, size);
    }

    // Registra una entrada o salida (venta/merma) y actualiza el stock atonico
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse registrar(@Valid @RequestBody MovimientoRequest request) {
        return movimientoService.registrarMovimiento(request);
    }

    // Ajuste a stock objetivo (RF-011)
    @PostMapping("/ajuste")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse ajustar(@Valid @RequestBody AjusteRequest request) {
        return movimientoService.registrarAjuste(request);
    }

    // Historico de conciliaciones (RF-011): anterior/nueva de cada ajuste
    @GetMapping("/ajustes")
    public List<AjusteResponse> listarAjustes() {
        return movimientoService.listarAjustes();
    }

    // Anula un movimiento y revierte su efecto sobre el stock
    @PostMapping("/{id}/anular")
    public MovimientoResponse anular(@PathVariable Integer id) {
        return movimientoService.anular(id);
    }
}