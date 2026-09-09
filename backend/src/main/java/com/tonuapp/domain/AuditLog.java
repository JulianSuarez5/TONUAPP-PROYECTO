package com.tonuapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro append-only de auditoria (RF-011 + requisito general: QUIEN hizo QUE, CUANDO
 * y SOBRE QUE registro). La alimenta el interceptor AOP {@code AuditAspect}.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "entidad", nullable = false, length = 100)
    private String entidad;

    @Column(name = "id_registro", nullable = false, length = 50)
    private String idRegistro;

    @Column(name = "operacion", nullable = false, length = 20)
    private String operacion;

    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "valores_antes", columnDefinition = "NVARCHAR(MAX)")
    private String valoresAntes;

    @Column(name = "valores_despues", columnDefinition = "NVARCHAR(MAX)")
    private String valoresDespues;

    // Asociacion SOLO LECTURA (insertable/updatable = false): el id_usuario se escribe
    // manualmente en AuditService; esta relacion solo permite incluir nombre/correo del
    // autor en la consulta de auditoria (GET /api/auditoria, Fase 10)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", insertable = false, updatable = false)
    private Usuario usuario;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public String getIdRegistro() {
        return idRegistro;
    }

    public void setIdRegistro(String idRegistro) {
        this.idRegistro = idRegistro;
    }

    public String getOperacion() {
        return operacion;
    }

    public void setOperacion(String operacion) {
        this.operacion = operacion;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getValoresAntes() {
        return valoresAntes;
    }

    public void setValoresAntes(String valoresAntes) {
        this.valoresAntes = valoresAntes;
    }

    public String getValoresDespues() {
        return valoresDespues;
    }

    public void setValoresDespues(String valoresDespues) {
        this.valoresDespues = valoresDespues;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }
}