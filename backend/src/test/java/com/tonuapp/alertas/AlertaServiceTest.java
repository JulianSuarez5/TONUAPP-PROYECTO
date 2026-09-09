package com.tonuapp.alertas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.audit.AuditService;
import com.tonuapp.domain.AlertaEstado;
import com.tonuapp.domain.AlertaInventario;
import com.tonuapp.domain.Material;
import com.tonuapp.repository.AlertaInventarioRepository;
import com.tonuapp.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;

class AlertaServiceTest {

    private AlertaInventarioRepository alertaRepository;
    private AuditService auditService;
    private AlertaService alertaService;

    @BeforeEach
    void setUp() {
        alertaRepository = mock(AlertaInventarioRepository.class);
        auditService = mock(AuditService.class);
        alertaService = new AlertaService(alertaRepository, auditService);
    }

    private Material materialCon(int stock, int minimo) {
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Cemento");
        m.setStock(new BigDecimal(stock));
        m.setStockMinimo(new BigDecimal(minimo));
        return m;
    }

    @Test
    @DisplayName("generar: crea alerta activa cuando el stock quedo bajo el minimo")
    void generaAlerta() {
        Material m = materialCon(5, 10);
        when(alertaRepository.findFirstByMaterial_IdMaterialAndEstadoOrderByFechaGeneradaDesc(1, AlertaEstado.activa))
                .thenReturn(Optional.empty());
        when(alertaRepository.save(any(AlertaInventario.class))).thenAnswer(inv -> {
            AlertaInventario a = inv.getArgument(0);
            a.setIdAlerta(5);
            return a;
        });

        AlertaInventario alerta = alertaService.generarSiCorresponde(m);

        assertThat(alerta).isNotNull();
        assertThat(alerta.getIdAlerta()).isEqualTo(5);
        assertThat(alerta.getEstado()).isEqualTo(AlertaEstado.activa);
        assertThat(alerta.getMaterial()).isEqualTo(m);
        assertThat(alerta.getMensaje()).contains("5");
        assertThat(alerta.getMensaje()).contains("10");
        verify(alertaRepository).save(any(AlertaInventario.class));
        // auditoria explicita al CREAR (id_registro = id_alerta, no del material)
        verify(auditService).registrar(eq("alertas_inventario"), eq("5"), eq("CREATE"), isNull(), isNull(), anyString());
    }

    @Test
    @DisplayName("generar: stock igual o superior al minimo -> no crea alerta ni audita")
    void stockSobreMinimoNoAlerta() {
        Material m = materialCon(10, 10);

        AlertaInventario alerta = alertaService.generarSiCorresponde(m);

        assertThat(alerta).isNull();
        verify(auditService, never()).registrar(anyString(), anyString(), anyString(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("generar: stock_minimo nulo -> no crea alerta")
    void stockMinimoNuloNoAlerta() {
        Material m = materialCon(0, 0);
        m.setStockMinimo(null);

        AlertaInventario alerta = alertaService.generarSiCorresponde(m);

        assertThat(alerta).isNull();
    }

    @Test
    @DisplayName("generar: ya existe alerta activa -> no duplica (D-20)")
    void noDuplicaConActiva() {
        Material m = materialCon(5, 10);
        when(alertaRepository.findFirstByMaterial_IdMaterialAndEstadoOrderByFechaGeneradaDesc(1, AlertaEstado.activa))
                .thenReturn(Optional.of(new AlertaInventario()));

        AlertaInventario alerta = alertaService.generarSiCorresponde(m);

        assertThat(alerta).isNull();
    }

    @Test
    @DisplayName("atender: pasa de activa a atendida")
    void atenderActiva() {
        Material m = materialCon(5, 10);
        AlertaInventario alerta = new AlertaInventario();
        alerta.setIdAlerta(3);
        alerta.setMaterial(m);
        alerta.setEstado(AlertaEstado.activa);
        when(alertaRepository.findById(3)).thenReturn(Optional.of(alerta));
        when(alertaRepository.save(any(AlertaInventario.class))).thenReturn(alerta);

        var resp = alertaService.atender(3);

        assertThat(resp.estado()).isEqualTo(AlertaEstado.atendida);
    }

    @Test
    @DisplayName("atender: alerta ya atendida -> 409")
    void atenderYaAtendida() {
        AlertaInventario alerta = new AlertaInventario();
        alerta.setIdAlerta(3);
        alerta.setEstado(AlertaEstado.atendida);
        when(alertaRepository.findById(3)).thenReturn(Optional.of(alerta));

        assertThatThrownBy(() -> alertaService.atender(3))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("atender: alerta inexistente -> 404")
    void atenderInexistente() {
        when(alertaRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertaService.atender(99))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}