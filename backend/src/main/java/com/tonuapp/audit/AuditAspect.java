package com.tonuapp.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tonuapp.security.AuthenticatedUser;
import com.tonuapp.security.SecurityUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Interceptor AOP que escribe en audit_log despues de cada operacion marcada con
 * {@link Auditable}. Registra:
 *  - entidad / operacion (de la anotacion)
 *  - id_registro (extraido del resultado o del primer argumento si tiene getId)
 *  - id_usuario (del SecurityContext)
 *  - valores_antes / valores_despues (JSON del argumento y del resultado)
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditService auditService;
    private final ObjectMapper mapper;

    public AuditAspect(AuditService auditService, ObjectMapper mapper) {
        this.auditService = auditService;
        this.mapper = mapper;
    }

    // Se dispara tras cada metodo anotado con @Auditable: serializa argumento y resultado
    // y registra en audit_log quien/que/cuando/sobre-que registro
    // TODO: en UPDATE/DELETE, valores_antes solo guarda el argumento (el id) y no el
    // estado previo real del registro en BD. Para auditoria completa habria que leer el
    // estado anterior antes de la operacion (mejora pendiente, Fase 10 endurecimiento)
    @AfterReturning(value = "@annotation(com.tonuapp.audit.Auditable)", returning = "result")
    public void auditar(JoinPoint joinPoint, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Auditable ann = method.getAnnotation(Auditable.class);

            Object[] args = joinPoint.getArgs();
            Object ant = args.length > 0 ? args[0] : null;

            String idRegistro = extractId(result != null ? result : ant);
            String valoresAntes = toJson(ant);
            String valoresDespues = toJson(result);
            Integer idUsuario = SecurityUtils.currentUser()
                    .map(AuthenticatedUser::idUsuario)
                    .orElse(null);

            auditService.registrar(ann.entidad(), idRegistro, ann.operacion(),
                    idUsuario, valoresAntes, valoresDespues);
        } catch (Exception e) {
            log.error("Error al registrar auditoria", e);
        }
    }

    // Extrae el id del registro auditado: si el arg es un Number es el id directo (p.ej.
    // desactivar(Integer id)); si es entidad/DTO intenta los accessors conocidos
    private String extractId(Object value) {
        if (value == null) {
            return "0";
        }
        // Cuando el argumento es directamente el id (p.ej. desactivar(Integer id)).
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        // Identificador primario de las entidades/DTOs conocidas. Se prueba primero el
        // accessor record-style (idMovimiento()) y luego el bean-style (getIdMovimiento()).
        // Orden deliberado: se privilegia la PK de la ENTIDAD auditada (idMovimiento,
        // idAjuste, idMaterial...) antes que ids de contexto como idUsuario/idMaterial que
        // tambien aparecen en los DTOs de respuesta (corrige id_registro=idUsuario en
        // movimientos). Para la entidad usuarios solo expone idUsuario y se captura igual.
        String[] candidatos = {"idMovimiento", "idAjuste", "idAlerta", "idMaterial",
                "idProveedor", "idRol", "idLote", "idUbicacion", "idZona", "idUsuario", "id"};
        for (String candidato : candidatos) {
            String beanGetter = "get" + Character.toUpperCase(candidato.charAt(0)) + candidato.substring(1);
            for (String methodName : new String[]{candidato, beanGetter}) {
                try {
                    Method m = value.getClass().getMethod(methodName);
                    Object id = m.invoke(value);
                    if (id != null) {
                        return String.valueOf(id);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // continuar con el siguiente
                }
            }
        }
        return "0";
    }

    // Serializa el valor a JSON para guardarlo en valores_antes/valores_despues
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"error\":\"serializacion\"}";
        }
    }
}