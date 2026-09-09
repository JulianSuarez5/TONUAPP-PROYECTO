package com.tonuapp.repository;

import com.tonuapp.domain.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Consulta de auditoria con filtros opcionales (Fase 10 / D-22): cada parametro
    // nulo se ignora. El JOIN a usuarios es la relacion sol-lectura de AuditLog,
    // necesaria para exponer quien/nombre del autor en el DTO.
    // La lista SIEMPRE llega acotada (Pageable): audit_log crece indefinidamente y el
    // consultante solo ve los N mas recientes (D-23), nunca la tabla completa.
    @Query("""
            SELECT a FROM AuditLog a
            LEFT JOIN a.usuario u
            WHERE (:entidad IS NULL OR a.entidad = :entidad)
              AND (:operacion IS NULL OR a.operacion = :operacion)
              AND (:idUsuario IS NULL OR a.idUsuario = :idUsuario)
              AND (:desde IS NULL OR a.fecha >= :desde)
              AND (:hasta IS NULL OR a.fecha <= :hasta)
            ORDER BY a.fecha DESC
            """)
    List<AuditLog> buscar(@Param("entidad") String entidad,
                          @Param("operacion") String operacion,
                          @Param("idUsuario") Integer idUsuario,
                          @Param("desde") LocalDateTime desde,
                          @Param("hasta") LocalDateTime hasta,
                          Pageable pageable);
}