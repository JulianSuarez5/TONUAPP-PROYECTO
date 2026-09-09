package com.tonuapp.movimientos;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.domain.TipoMovimiento;
import com.tonuapp.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class MovimientoMapperTest {

    @Test
    @DisplayName("toResponse: resuelve material/usuario y copia el resto del movimiento")
    void toResponse() {
        Material m = new Material();
        m.setIdMaterial(2);
        m.setNombre("Gravilla media");

        Usuario u = new Usuario();
        u.setIdUsuario(1);
        u.setNombre("Admin Prueba");

        MovimientoInventario mov = new MovimientoInventario();
        mov.setIdMovimiento(3);
        mov.setMaterial(m);
        mov.setUsuario(u);
        mov.setTipoMovimiento(TipoMovimiento.salida_venta);
        mov.setCantidad(new BigDecimal("7.5"));
        mov.setEstado(EstadoMovimiento.activo);
        mov.setMotivo("despacho");
        mov.setObservaciones("obra norte");
        mov.setFechaMovimiento(LocalDateTime.of(2026, 9, 4, 10, 0));

        var r = MovimientoMapper.toResponse(mov);

        assertThat(r.idMovimiento()).isEqualTo(3);
        assertThat(r.idMaterial()).isEqualTo(2);
        assertThat(r.nombreMaterial()).isEqualTo("Gravilla media");
        assertThat(r.nombreUsuario()).isEqualTo("Admin Prueba");
        assertThat(r.tipo()).isEqualTo(TipoMovimiento.salida_venta);
        assertThat(r.cantidad()).isEqualByComparingTo("7.5");
        assertThat(r.observaciones()).isEqualTo("obra norte");
    }
}