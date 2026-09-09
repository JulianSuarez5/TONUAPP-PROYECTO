package com.tonuapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Tabla puente N:M entre materiales y proveedores (D-01/D-14). Solo guarda los ids y la
 * marca es_principal (proveedor principal de ese material); el nombre/detalle de ambos se
 * resuelve en el ProveedorService levantando las entidades por id.
 */
@Entity
@Table(name = "material_proveedor")
@IdClass(MaterialProveedorId.class)
public class MaterialProveedor {

    @Id
    @Column(name = "id_material")
    private Integer materialId;

    @Id
    @Column(name = "id_proveedor")
    private Integer proveedorId;

    @Column(name = "es_principal", nullable = false)
    private boolean esPrincipal = false;

    @Column(name = "fecha_asociacion", nullable = false)
    private LocalDateTime fechaAsociacion;

    public Integer getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Integer materialId) {
        this.materialId = materialId;
    }

    public Integer getProveedorId() {
        return proveedorId;
    }

    public void setProveedorId(Integer proveedorId) {
        this.proveedorId = proveedorId;
    }

    public boolean isEsPrincipal() {
        return esPrincipal;
    }

    public void setEsPrincipal(boolean esPrincipal) {
        this.esPrincipal = esPrincipal;
    }

    public LocalDateTime getFechaAsociacion() {
        return fechaAsociacion;
    }

    public void setFechaAsociacion(LocalDateTime fechaAsociacion) {
        this.fechaAsociacion = fechaAsociacion;
    }
}