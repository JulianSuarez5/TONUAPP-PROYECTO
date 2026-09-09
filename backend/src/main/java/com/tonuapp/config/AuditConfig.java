package com.tonuapp.config;

import com.tonuapp.audit.AuditAspect;
import com.tonuapp.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Registra el aspect de auditoria (haría falta @Component en AuditAspect; mejor se
 * declara aquí con sus dependencias explicitas).
 */
@Configuration
@EnableAspectJAutoProxy
public class AuditConfig {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditConfig(AuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    // Registra el aspect como bean con sus dependencias (asi no necesita @Component)
    @Bean
    public AuditAspect auditAspect() {
        return new AuditAspect(auditService, objectMapper);
    }
}