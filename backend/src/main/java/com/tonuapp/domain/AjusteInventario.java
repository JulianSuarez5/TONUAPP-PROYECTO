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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle fino de cada ajuste (RF-011 conciliacion, D-18/D-19): guarda cantidad
 * anterior y nueva por si hay que anular el movimiento 'ajuste' y revertir el
 * stock al valor contado antes. Se relaciona 1:1 con movimientos_inventario via
 * id_movimiento (nullable) en sentido ajustes -> movimiento.
 */
@Entity
@Table(name = "ajustes_inventario")
public class AjusteInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ajuste")
    private Integer idAjuste;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_material", nullable = false)
    private Material material;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_movimiento")
    private MovimientoInventario movimiento;

    @Column(name = "cantidad_anterior", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadAnterior;

    @Column(name = "cantidad_nueva", nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidadNueva;

    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    @Column(name = "fecha_ajuste", nullable = false)
    private LocalDateTime fechaAjuste;

    public Integer getIdAjuste() {
        return idAjuste;
    }

    public void setIdAjuste(Integer idAjuste) {
        this.idAjuste = idAjuste;
    }

    public Material getMaterial() {
        return material;
    }

    public void setMaterial(Material material) {
        this.material = material;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public MovimientoInventario getMovimiento() {
        return movimiento;
    }

    public void setMovimiento(MovimientoInventario movimiento) {
        this.movimiento = movimiento;
    }

    public BigDecimal getCantidadAnterior() {
        return cantidadAnterior;
    }

    public void setCantidadAnterior(BigDecimal cantidadAnterior) {
        this.cantidadAnterior = cantidadAnterior;
    }

    public BigDecimal getCantidadNueva() {
        return cantidadNueva;
    }

    public void setCantidadNueva(BigDecimal cantidadNueva) {
        this.cantidadNueva = cantidadNueva;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFechaAjuste() {
        return fechaAjuste;
    }

    public void setFechaAjuste(LocalDateTime fechaAjuste) {
        this.fechaAjuste = fechaAjuste;
    }
}