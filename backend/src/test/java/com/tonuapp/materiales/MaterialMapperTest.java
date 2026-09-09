package com.tonuapp.materiales;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonuapp.domain.Categoria;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.UnidadMedida;
import com.tonuapp.domain.ZonaAcopio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class MaterialMapperTest {

    @Test
    @DisplayName("toResponse mapea todos los campos esperados")
    void mapeaEntidadAResponse() {
        Categoria categoria = new Categoria();
        categoria.setIdCategoria(2);
        categoria.setNombre("Cemento");

        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidad(4);
        unidad.setNombre("Kilogramo");
        unidad.setAbreviatura("kg");

        Material m = new Material();
        m.setIdMaterial(7);
        m.setNombre("Cemento Gris");
        m.setCategoria(categoria);
        m.setUnidad(unidad);
        m.setStock(new BigDecimal("120.00"));
        m.setStockMinimo(new BigDecimal("50.00"));
        m.setActivo(true);
        m.setFechaRegistro(LocalDateTime.of(2026, 9, 4, 10, 0));

        ZonaAcopio zona = new ZonaAcopio();
        zona.setIdZona(3);
        zona.setNombreZona("Patio Sur");
        m.setZona(zona);

        var resp = MaterialMapper.toResponse(m);

        assertThat(resp.idMaterial()).isEqualTo(7);
        assertThat(resp.nombre()).isEqualTo("Cemento Gris");
        assertThat(resp.idCategoria()).isEqualTo(2);
        assertThat(resp.categoriaNombre()).isEqualTo("Cemento");
        assertThat(resp.idUnidad()).isEqualTo(4);
        assertThat(resp.unidadNombre()).isEqualTo("Kilogramo");
        assertThat(resp.unidadAbreviatura()).isEqualTo("kg");
        assertThat(resp.stock()).isEqualByComparingTo("120.00");
        assertThat(resp.stockMinimo()).isEqualByComparingTo("50.00");
        assertThat(resp.activo()).isTrue();
        assertThat(resp.fechaRegistro()).isEqualTo(LocalDateTime.of(2026, 9, 4, 10, 0));
        assertThat(resp.idZona()).isEqualTo(3);
        assertThat(resp.zonaNombre()).isEqualTo("Patio Sur");
    }

    @Test
    @DisplayName("toResponse: sin zona asignada, idZona/zonaNombre son nulos")
    void sinZonaAsignada() {
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Arena de rio");
        m.setCategoria(categoria());
        m.setUnidad(unidad());
        m.setStock(BigDecimal.TEN);
        m.setActivo(true);

        var resp = MaterialMapper.toResponse(m);

        assertThat(resp.idZona()).isNull();
        assertThat(resp.zonaNombre()).isNull();
    }

    private Categoria categoria() {
        Categoria categoria = new Categoria();
        categoria.setIdCategoria(1);
        categoria.setNombre("Arena");
        return categoria;
    }

    private UnidadMedida unidad() {
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidad(3);
        unidad.setNombre("Metro cubico");
        unidad.setAbreviatura("m3");
        return unidad;
    }
}