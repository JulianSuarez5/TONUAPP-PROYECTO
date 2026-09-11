package com.tonuapp.reportes;

import com.tonuapp.movimientos.dto.MovimientoResponse;
import com.tonuapp.reportes.dto.ReporteGeneradoResponse;
import com.tonuapp.reportes.dto.ReporteInventarioRow;
import com.tonuapp.reportes.dto.ReporteRequest;
import com.tonuapp.reportes.dto.ReporteResumenRow;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Endpoints de reportes de inventario (RF-014). Solo Administrador: el rol "Gerente"
 * mencionado en RF-014 sigue pendiente de la decision de roles (RF-006).
 * El cliente consulta el stock en tiempo real por /api/materiales (RF-001).
 */
@RestController
@RequestMapping("/api/reportes")
@PreAuthorize("hasRole('Administrador')")
public class ReporteController {

    private final ReporteService reporteService;

    public ReporteController(ReporteService reporteService) {
        this.reporteService = reporteService;
    }

    // Stock actual de todos los materiales activos (RF-001)
    @GetMapping("/inventario")
    public List<ReporteInventarioRow> inventario() {
        return reporteService.listarInventario();
    }

    // Materiales bajo el stock minimo (RF-012), del mas critico al menos critico
    @GetMapping("/bajo-stock")
    public List<ReporteInventarioRow> bajoStock() {
        return reporteService.bajoStock();
    }

    // Historial de movimientos en un rango de fechas (RF-014); sin rango devuelve todo
    @GetMapping("/movimientos")
    public List<MovimientoResponse> movimientos(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return reporteService.listarMovimientos(desde, hasta);
    }

    // Totales por material en el rango (RF-013: mermas clasificadas)
    @GetMapping("/resumen")
    public List<ReporteResumenRow> resumen(
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta) {
        return reporteService.resumen(desde, hasta);
    }

    // Genera y descarga el reporte en el formato pedido; registra la bitacora
    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@Valid @RequestBody ReporteRequest request) {
        ReporteService.ResultadoExportacion resultado = reporteService.exportar(request);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(resultado.contentType()));
        headers.setContentDispositionFormData("attachment", resultado.nombreArchivo());
        headers.add("X-Reporte-Id", String.valueOf(resultado.idReporte()));
        return new ResponseEntity<>(resultado.contenido(), headers, HttpStatus.OK);
    }

    // Historial de exportaciones (RF-014): quien, cuando, tipo, formato y rango
    @GetMapping("/bitacora")
    public List<ReporteGeneradoResponse> bitacora() {
        return reporteService.bitacora();
    }

    // Re-descarga el archivo generado previamente, desde la bitacora
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Integer id) {
        ReporteService.DescargaResultado resultado = reporteService.descargar(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(resultado.contentType()));
        headers.setContentDispositionFormData("attachment", resultado.nombreArchivo());
        return new ResponseEntity<>(resultado.contenido(), headers, HttpStatus.OK);
    }
}