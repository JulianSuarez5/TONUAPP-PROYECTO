package com.tonuapp.alertas;

import com.tonuapp.alertas.dto.AlertaResponse;
import com.tonuapp.domain.AlertaEstado;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints de alertas de bajo stock (RF-012). Panel operativo: solo Administrador ve
 * las alertas y las cierra; el Cliente no gestiona inventario.
 */
@RestController
@RequestMapping("/api/alertas")
@PreAuthorize("hasRole('Administrador')")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    // Lista alertas (mas recientes primero); filtros opcionales ?estado=activa y ?idMaterial
    @GetMapping
    public List<AlertaResponse> listar(@RequestParam(required = false) AlertaEstado estado,
                                       @RequestParam(required = false) Integer idMaterial) {
        return alertaService.listar(estado, idMaterial);
    }

    // Cierra manualmente una alerta activa (D-20)
    @PostMapping("/{id}/atender")
    public AlertaResponse atender(@PathVariable Integer id) {
        return alertaService.atender(id);
    }
}