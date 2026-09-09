package com.tonuapp.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un metodo de servicio como auditable. El {@link AuditAspect} registra en
 * audit_log quien, que, cuando y sobre que registro (RF-011 + auditoria general).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Nombre de la entidad (columna audit_log.entidad). */
    String entidad();

    /** CREATE / UPDATE / DELETE / MOVEMENT / ADJUST (CHECK de la tabla audit_log). */
    String operacion();
}