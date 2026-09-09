package com.tonuapp.audit;

import com.tonuapp.domain.AuditLog;
import com.tonuapp.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia de auditoria. Requiere una transaccion propia (REQUIRES_NEW) para que el
 * registro de auditoria se guarde incluso si la operacion de negocio hace rollback.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    // Guarda un registro de auditoria. Usa transaccion PROPIA (REQUIRES_NEW) para que el
    // log se conserve aunque la operacion de negocio haga rollback
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String entidad, String idRegistro, String operacion,
                          Integer idUsuario, String valoresAntes, String valoresDespues) {
        AuditLog log = new AuditLog();
        log.setEntidad(entidad);
        log.setIdRegistro(idRegistro);
        log.setOperacion(operacion);
        log.setIdUsuario(idUsuario);
        log.setFecha(java.time.LocalDateTime.now());
        log.setValoresAntes(valoresAntes);
        log.setValoresDespues(valoresDespues);
        repository.save(log);
    }
}