package com.tonuapp.movimientos;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonuapp.domain.AjusteInventario;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class AjusteMapperTest {

    @Test
    @DisplayName("toResponse: resuelve material/usuario/movimiento y copia anterior y nueva")
    void toResponse() {
        Material m = new Material();
        m.setIdMaterial(3);
        m.setNombre("Bloque de concreto");

        Usuario u = new Usuario();
        u.setIdUsuario(1);
        u.setNombre("Admin Prueba");

        MovimientoInventario mov = new MovimientoInventario();
        mov.setIdMovimiento(9);

        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setIdAjuste(2);
        ajuste.setMaterial(m);
        ajuste.setUsuario(u);
        ajuste.setMovimiento(mov);
        ajuste.setCantidadAnterior(new BigDecimal("10"));
        ajuste.setCantidadNueva(new BigDecimal("25"));
        ajuste.setMotivo("conteo fisico");
        ajuste.setFechaAjuste(LocalDateTime.of(2026, 9, 4, 11, 30));

        var r = AjusteMapper.toResponse(ajuste);

        assertThat(r.idAjuste()).isEqualTo(2);
        assertThat(r.idMaterial()).isEqualTo(3);
        assertThat(r.nombreMaterial()).isEqualTo("Bloque de concreto");
        assertThat(r.idMovimiento()).isEqualTo(9);
        assertThat(r.cantidadAnterior()).isEqualByComparingTo("10");
        assertThat(r.cantidadNueva()).isEqualByComparingTo("25");
        assertThat(r.motivo()).isEqualTo("conteo fisico");
        assertThat(r.fechaAjuste()).isEqualTo(LocalDateTime.of(2026, 9, 4, 11, 30));
    }

    @Test
    @DisplayName("toResponse: tolera ajuste sin movimiento asociado (idMovimiento null)")
    void toResponseSinMovimiento() {
        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setIdAjuste(3);
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Cemento");
        Usuario u = new Usuario();
        u.setIdUsuario(2);
        u.setNombre("Cliente Prueba");
        ajuste.setMaterial(m);
        ajuste.setUsuario(u);
        ajuste.setCantidadAnterior(new BigDecimal("5"));
        ajuste.setCantidadNueva(new BigDecimal("5"));
        ajuste.setMotivo("sin movimiento");
        ajuste.setFechaAjuste(LocalDateTime.now());

        var r = AjusteMapper.toResponse(ajuste);

        assertThat(r.idMovimiento()).isNull();
        assertThat(r.cantidadAnterior()).isEqualByComparingTo("5");
    }
}