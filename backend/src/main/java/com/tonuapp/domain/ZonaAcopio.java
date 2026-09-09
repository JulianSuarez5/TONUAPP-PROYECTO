package com.tonuapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Zona de acopio de materiales (RF-015, US-16). Nombre unico, capacidad maxima no
 * negativa, numero inmutable (D-04). El tipo de material permitido es opcional y se usa
 * para validar que una zona no se asigne a materiales incompatibles simultaneamente
 * (la zona se relaciona con materiales via materiales.id_zona, RF-015).
 */
@Entity
@Table(name = "zonas_acopio")
public class ZonaAcopio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_zona")
    private Integer idZona;

    @Column(name = "nombre_zona", nullable = false, length = 100, unique = true)
    private String nombreZona;

    @Column(name = "capacidad_maxima", nullable = false, precision = 12, scale = 2)
    private BigDecimal capacidadMaxima;

    @Column(name = "tipo_material_permitido", length = 100)
    private String tipoMaterialPermitido;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    public Integer getIdZona() {
        return idZona;
    }

    public void setIdZona(Integer idZona) {
        this.idZona = idZona;
    }

    public String getNombreZona() {
        return nombreZona;
    }

    public void setNombreZona(String nombreZona) {
        this.nombreZona = nombreZona;
    }

    public BigDecimal getCapacidadMaxima() {
        return capacidadMaxima;
    }

    public void setCapacidadMaxima(BigDecimal capacidadMaxima) {
        this.capacidadMaxima = capacidadMaxima;
    }

    public String getTipoMaterialPermitido() {
        return tipoMaterialPermitido;
    }

    public void setTipoMaterialPermitido(String tipoMaterialPermitido) {
        this.tipoMaterialPermitido = tipoMaterialPermitido;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}