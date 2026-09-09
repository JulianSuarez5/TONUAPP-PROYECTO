package com.tonuapp.proveedores;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonuapp.domain.Material;
import com.tonuapp.domain.MaterialProveedor;
import com.tonuapp.domain.Proveedor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class ProveedorMapperTest {

    @Test
    @DisplayName("toResponse: mapea el proveedor con su cantidad de materiales")
    void toResponse() {
        Proveedor p = new Proveedor();
        p.setIdProveedor(7);
        p.setNombre("Tienda Agro");
        p.setNit("900123456");

        var resp = ProveedorMapper.toResponse(p, 3L);

        assertThat(resp.idProveedor()).isEqualTo(7);
        assertThat(resp.nombre()).isEqualTo("Tienda Agro");
        assertThat(resp.nit()).isEqualTo("900123456");
        assertThat(resp.cantidadMateriales()).isEqualTo(3L);
    }

    @Test
    @DisplayName("toMaterialProveedorResponse: resuelve nombres de material y proveedor")
    void toMaterialProveedorResponse() {
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Arena de rio");

        Proveedor p = new Proveedor();
        p.setIdProveedor(2);
        p.setNombre("Tienda Agro");

        MaterialProveedor fila = new MaterialProveedor();
        fila.setMaterialId(1);
        fila.setProveedorId(2);
        fila.setEsPrincipal(true);
        fila.setFechaAsociacion(LocalDateTime.of(2026, 9, 4, 10, 0));

        var resp = ProveedorMapper.toMaterialProveedorResponse(fila, m, p);

        assertThat(resp.idMaterial()).isEqualTo(1);
        assertThat(resp.nombreMaterial()).isEqualTo("Arena de rio");
        assertThat(resp.idProveedor()).isEqualTo(2);
        assertThat(resp.nombreProveedor()).isEqualTo("Tienda Agro");
        assertThat(resp.esPrincipal()).isTrue();
        assertThat(resp.fechaAsociacion()).isEqualTo(LocalDateTime.of(2026, 9, 4, 10, 0));
    }
}