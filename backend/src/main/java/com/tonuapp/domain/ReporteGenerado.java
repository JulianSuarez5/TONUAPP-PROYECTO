package com.tonuapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Bitacora de exportaciones de reportes (RF-014): cada archivo generado (pdf/xlsx)
 * se registra con QUIEN lo pidio, CUANDO, de que tipo/formato y el rango consultado.
 * La tabla NO guarda el binario: ruta_archivo apunta al archivo fisico generado
 * (nombre generado, sin rutas absolutas).
 */
@Entity
@Table(name = "reportes_generados")
public class ReporteGenerado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reporte")
    private Integer idReporte;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Column(name = "tipo_reporte", nullable = false, length = 50)
    private String tipoReporte;

    @Column(name = "formato", nullable = false, length = 10)
    private String formato;

    @Column(name = "fecha_generacion", nullable = false)
    private LocalDateTime fechaGeneracion;

    @Column(name = "rango_fecha_inicio")
    private LocalDate rangoFechaInicio;

    @Column(name = "rango_fecha_fin")
    private LocalDate rangoFechaFin;

    @Column(name = "ruta_archivo", length = 300)
    private String rutaArchivo;

    public Integer getIdReporte() {
        return idReporte;
    }

    public void setIdReporte(Integer idReporte) {
        this.idReporte = idReporte;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getTipoReporte() {
        return tipoReporte;
    }

    public void setTipoReporte(String tipoReporte) {
        this.tipoReporte = tipoReporte;
    }

    public String getFormato() {
        return formato;
    }

    public void setFormato(String formato) {
        this.formato = formato;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }

    public LocalDate getRangoFechaInicio() {
        return rangoFechaInicio;
    }

    public void setRangoFechaInicio(LocalDate rangoFechaInicio) {
        this.rangoFechaInicio = rangoFechaInicio;
    }

    public LocalDate getRangoFechaFin() {
        return rangoFechaFin;
    }

    public void setRangoFechaFin(LocalDate rangoFechaFin) {
        this.rangoFechaFin = rangoFechaFin;
    }

    public String getRutaArchivo() {
        return rutaArchivo;
    }

    public void setRutaArchivo(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }
}