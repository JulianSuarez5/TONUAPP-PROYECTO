package com.tonuapp.alertas;

import com.tonuapp.alertas.dto.AlertaResponse;
import com.tonuapp.audit.AuditService;
import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.AlertaEstado;
import com.tonuapp.domain.AlertaInventario;
import com.tonuapp.domain.Material;
import com.tonuapp.repository.AlertaInventarioRepository;
import com.tonuapp.security.AuthenticatedUser;
import com.tonuapp.security.SecurityUtils;
import com.tonuapp.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Alertas de baja disponibilidad (RF-012, D-05/D-20). Reglas:
 *  - se genera UNA alerta activa por material y por episodio de bajo stock (si ya hay
 *    una activa, los movimientos que sigan bajo el minimo NO crean duplicados).
 *  - la alerta se cierra MANUALMENTE por el Administrador (atender); no se auto-resuelve
 *    al subir el stock (D-20). Quien/cuando la atendio queda en audit_log (D-06).
 *  - generarSiCorresponde se invoca desde MovimientoService en la MISMA transaccion que
 *    el movimiento: si este se revierte, tambien se revierte la alerta.
 */
@Service
public class AlertaService {

    private final AlertaInventarioRepository alertaRepository;
    private final AuditService auditService;

    public AlertaService(AlertaInventarioRepository alertaRepository, AuditService auditService) {
        this.alertaRepository = alertaRepository;
        this.auditService = auditService;
    }

    // Tras una operacion de stock: si el material quedo bajo el minimo y no tiene otra
    // alerta activa, crea una nueva (retorna null si no corresponde). La auditoria se
    // registra EXPLICITAMENTE al crear (no con @Auditable) para no escribir filas
    // basura en audit_log cuando no se crea ninguna alerta (ver D-20)
    @Transactional
    public AlertaInventario generarSiCorresponde(Material m) {
        BigDecimal minimo = m.getStockMinimo();
        if (minimo == null || m.getStock().compareTo(minimo) >= 0) {
            return null;
        }
        boolean yaActiva = alertaRepository
                .findFirstByMaterial_IdMaterialAndEstadoOrderByFechaGeneradaDesc(m.getIdMaterial(), AlertaEstado.activa)
                .isPresent();
        if (yaActiva) {
            return null;
        }

        AlertaInventario alerta = new AlertaInventario();
        alerta.setMaterial(m);
        alerta.setFechaGenerada(LocalDateTime.now());
        alerta.setEstado(AlertaEstado.activa);
        alerta.setMensaje("Baja disponibilidad del material %s: stock actual %s, minimo %s."
                .formatted(m.getNombre(), m.getStock().toPlainString(), minimo.toPlainString()));
        AlertaInventario guardada = alertaRepository.save(alerta);
        registrarCreacion(guardada);
        return guardada;
    }

    // Quien/cuando/sobre que registro (D-06): indice id_alerta y contexto minimo
    // del material afectado; se usa REQUIRES_NEW de AuditService igual que el resto
    private void registrarCreacion(AlertaInventario alerta) {
        Integer idUsuario = SecurityUtils.currentUser()
                .map(AuthenticatedUser::idUsuario)
                .orElse(null);
        String despues = "{\"id_alerta\":%d,\"id_material\":%d}"
                .formatted(alerta.getIdAlerta(), alerta.getMaterial().getIdMaterial());
        auditService.registrar("alertas_inventario", String.valueOf(alerta.getIdAlerta()),
                "CREATE", idUsuario, null, despues);
    }

    // Historico de alertas (mas reciente primero); filtros opcionales por estado y material
    public List<AlertaResponse> listar(AlertaEstado estado, Integer idMaterial) {
        List<AlertaInventario> alertas = idMaterial != null
                ? alertaRepository.findAllByMaterial_IdMaterialOrderByFechaGeneradaDesc(idMaterial)
                : estado != null
                        ? alertaRepository.findAllByEstadoOrderByFechaGeneradaDesc(estado)
                        : alertaRepository.findAllByOrderByFechaGeneradaDesc();
        return alertas.stream().map(AlertaMapper::toResponse).toList();
    }

    // Cierre manual de una alerta activa (D-20): queda 'atendida'. Auditada con UPDATE
    @Auditable(entidad = "alertas_inventario", operacion = "UPDATE")
    @Transactional
    public AlertaResponse atender(Integer idAlerta) {
        AlertaInventario alerta = alertaRepository.findById(idAlerta)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Alerta no encontrada."));
        if (alerta.getEstado() != AlertaEstado.activa) {
            throw new ApiException(HttpStatus.CONFLICT, "La alerta ya esta atendida.");
        }
        alerta.setEstado(AlertaEstado.atendida);
        return AlertaMapper.toResponse(alertaRepository.save(alerta));
    }
}