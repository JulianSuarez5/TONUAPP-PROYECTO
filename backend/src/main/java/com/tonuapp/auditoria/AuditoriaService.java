package com.tonuapp.auditoria;

import com.tonuapp.auditoria.dto.AuditoriaResponse;
import com.tonuapp.domain.AuditLog;
import com.tonuapp.domain.Usuario;
import com.tonuapp.repository.AuditLogRepository;
import com.tonuapp.shared.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Consulta del log de auditoria (Fase 10 / D-22). Permite saber QUIEN hizo QUE, CUANDO
 * y SOBRE QUE registro: filtra por entidad, operacion, autor y rango de fechas.
 * La lista SIEMPRE llega acotada (default 100, maximo 500, D-23): audit_log crece
 * indefinidamente y el consultante ve solo los N mas recientes.
 */
@Service
public class AuditoriaService {

    /** Cantidad maxima de registros que puede devolver una consulta (D-23). */
    public static final int MAX_SIZE = 500;

    /** Cantidad por defecto cuando el cliente no envía size (D-23). */
    public static final int DEFAULT_SIZE = 100;

    private final AuditLogRepository repository;

    public AuditoriaService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AuditoriaResponse> consultar(String entidad, String operacion,
                                             Integer idUsuario, LocalDate desde, LocalDate hasta,
                                             Integer size) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        int limite = validarSize(size);
        LocalDateTime desdeDt = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime hastaDt = hasta != null ? hasta.atTime(LocalTime.MAX) : null;
        Pageable pageable = PageRequest.of(0, limite);
        return repository.buscar(normalizar(entidad), normalizar(operacion), idUsuario, desdeDt, hastaDt, pageable)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private int validarSize(Integer size) {
        int limite = size != null ? size : DEFAULT_SIZE;
        if (limite < 1 || limite > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El parametro 'size' debe estar entre 1 y " + MAX_SIZE + ".");
        }
        return limite;
    }

    private AuditoriaResponse toResponse(AuditLog log) {
        Usuario autor = log.getUsuario();
        return new AuditoriaResponse(
                log.getId(),
                log.getEntidad(),
                log.getIdRegistro(),
                log.getOperacion(),
                log.getIdUsuario(),
                autor != null ? autor.getNombre() : null,
                autor != null ? autor.getCorreo() : null,
                log.getFecha(),
                log.getValoresAntes(),
                log.getValoresDespues());
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}