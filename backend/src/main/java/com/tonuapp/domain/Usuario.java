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
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "correo", nullable = false, length = 150, unique = true)
    private String correo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "es_admin_principal", nullable = false)
    private boolean esAdminPrincipal = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    // Bloqueo temporal por cuenta (RF-008 / D-22): contador de verificaciones fallidas
    // exclusivas de sesion y fecha-hasta del bloqueo activo (NULL = sin bloqueo)
    @Column(name = "intentos_login_fallidos", nullable = false)
    private int intentosLoginFallidos;

    @Column(name = "bloqueo_hasta")
    private LocalDateTime bloqueoHasta;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public boolean isEsAdminPrincipal() {
        return esAdminPrincipal;
    }

    public void setEsAdminPrincipal(boolean esAdminPrincipal) {
        this.esAdminPrincipal = esAdminPrincipal;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(LocalDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public int getIntentosLoginFallidos() {
        return intentosLoginFallidos;
    }

    public void setIntentosLoginFallidos(int intentosLoginFallidos) {
        this.intentosLoginFallidos = intentosLoginFallidos;
    }

    public LocalDateTime getBloqueoHasta() {
        return bloqueoHasta;
    }

    public void setBloqueoHasta(LocalDateTime bloqueoHasta) {
        this.bloqueoHasta = bloqueoHasta;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}