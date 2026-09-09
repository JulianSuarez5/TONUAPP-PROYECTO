package com.tonuapp.alertas;

import static org.assertj.core.api.Assertions.assertThat;

import com.tonuapp.domain.AlertaEstado;
import com.tonuapp.domain.AlertaInventario;
import com.tonuapp.domain.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

class AlertaMapperTest {

    @Test
    @DisplayName("toResponse: resuelve material y copia estado/mensaje/fecha")
    void toResponse() {
        Material m = new Material();
        m.setIdMaterial(4);
        m.setNombre("Concreto rapido");

        AlertaInventario alerta = new AlertaInventario();
        alerta.setIdAlerta(1);
        alerta.setMaterial(m);
        alerta.setFechaGenerada(LocalDateTime.of(2026, 9, 4, 12, 0));
        alerta.setEstado(AlertaEstado.activa);
        alerta.setMensaje("Stock bajo el minimo para el material");

        var r = AlertaMapper.toResponse(alerta);

        assertThat(r.idAlerta()).isEqualTo(1);
        assertThat(r.idMaterial()).isEqualTo(4);
        assertThat(r.nombreMaterial()).isEqualTo("Concreto rapido");
        assertThat(r.estado()).isEqualTo(AlertaEstado.activa);
        assertThat(r.mensaje()).contains("bajo el minimo");
        assertThat(r.fechaGenerada()).isEqualTo(LocalDateTime.of(2026, 9, 4, 12, 0));
    }
}