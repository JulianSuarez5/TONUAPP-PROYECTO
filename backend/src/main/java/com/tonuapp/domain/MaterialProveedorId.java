package com.tonuapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Clave compuesta de la tabla material_proveedor (id_material, id_proveedor).
 */
public class MaterialProveedorId implements Serializable {

    private Integer materialId;
    private Integer proveedorId;

    public MaterialProveedorId() {
    }

    public MaterialProveedorId(Integer materialId, Integer proveedorId) {
        this.materialId = materialId;
        this.proveedorId = proveedorId;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MaterialProveedorId that = (MaterialProveedorId) o;
        return java.util.Objects.equals(materialId, that.materialId)
                && java.util.Objects.equals(proveedorId, that.proveedorId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(materialId, proveedorId);
    }
}