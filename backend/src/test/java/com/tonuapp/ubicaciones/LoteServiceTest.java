package com.tonuapp.ubicaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.domain.Lote;
import com.tonuapp.domain.Material;
import com.tonuapp.repository.LoteRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.shared.ApiException;
import com.tonuapp.ubicaciones.dto.LoteRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class LoteServiceTest {

    private LoteRepository loteRepository;
    private MaterialRepository materialRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private LoteService loteService;

    private Material material;

    @BeforeEach
    void setUp() {
        loteRepository = mock(LoteRepository.class);
        materialRepository = mock(MaterialRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        loteService = new LoteService(loteRepository, materialRepository, movimientoRepository);

        material = new Material();
        material.setIdMaterial(1);
        material.setNombre("Arena de rio");
        material.setActivo(true);
    }

    private LoteRequest request(String codigo, String cantidad) {
        return new LoteRequest(1, codigo, new BigDecimal(cantidad));
    }

    private Lote loteGuardado() {
        Lote l = new Lote();
        l.setIdLote(1);
        l.setMaterial(material);
        l.setCodigoLote("L-2026-001");
        l.setCantidad(new BigDecimal("100.00"));
        l.setFechaIngreso(LocalDateTime.now());
        l.setActivo(true);
        return l;
    }

    @Test
    @DisplayName("crear: lote valido se guarda y devuelve response")
    void crearValido() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(material));
        when(loteRepository.existsByCodigoLote("L-2026-001")).thenReturn(false);
        when(loteRepository.save(any(Lote.class))).thenAnswer(inv -> {
            Lote l = inv.getArgument(0);
            l.setIdLote(1);
            return l;
        });

        var resp = loteService.crear(request("L-2026-001", "100.00"));

        assertThat(resp.idLote()).isEqualTo(1);
        assertThat(resp.codigoLote()).isEqualTo("L-2026-001");
        assertThat(resp.idMaterial()).isEqualTo(1);
        assertThat(resp.materialNombre()).isEqualTo("Arena de rio");
        assertThat(resp.cantidad()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("crear: rechaza codigo duplicado (409)")
    void crearCodigoDuplicado() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(material));
        when(loteRepository.existsByCodigoLote("L-2026-001")).thenReturn(true);

        assertThatThrownBy(() -> loteService.crear(request("L-2026-001", "100.00")))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("crear: rechaza material inexistente (404)")
    void crearMaterialInexistente() {
        when(materialRepository.findByIdMaterialAndActivoTrue(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loteService.crear(new LoteRequest(9, "L-X", new BigDecimal("10.00"))))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("actualizar: permite cambiar codigo y cantidad conservando el material")
    void actualizarValido() {
        when(loteRepository.findByIdLoteAndActivoTrue(1)).thenReturn(Optional.of(loteGuardado()));
        when(loteRepository.existsByCodigoLoteAndIdLoteNot("L-2026-002", 1)).thenReturn(false);
        when(loteRepository.save(any(Lote.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = loteService.actualizar(1, new LoteRequest(1, "L-2026-002", new BigDecimal("150.00")));

        assertThat(resp.codigoLote()).isEqualTo("L-2026-002");
        assertThat(resp.cantidad()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("actualizar: rechaza cambiar el material del lote (409, trazabilidad)")
    void actualizarCambioMaterial() {
        when(loteRepository.findByIdLoteAndActivoTrue(1)).thenReturn(Optional.of(loteGuardado()));
        when(loteRepository.existsByCodigoLoteAndIdLoteNot("L-2026-002", 1)).thenReturn(false);

        assertThatThrownBy(() -> loteService.actualizar(1, new LoteRequest(2, "L-2026-002", new BigDecimal("100.00"))))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("actualizar: rechaza codigo usado por otro lote (409)")
    void actualizarCodigoAjeno() {
        when(loteRepository.findByIdLoteAndActivoTrue(1)).thenReturn(Optional.of(loteGuardado()));
        when(loteRepository.existsByCodigoLoteAndIdLoteNot("L-2026-002", 1)).thenReturn(true);

        assertThatThrownBy(() -> loteService.actualizar(1, request("L-2026-002", "100.00")))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("desactivar: soft delete cuando no hay movimientos que lo referencien")
    void desactivarValido() {
        when(loteRepository.findByIdLoteAndActivoTrue(1)).thenReturn(Optional.of(loteGuardado()));
        when(movimientoRepository.countByIdLote(1)).thenReturn(0L);

        loteService.desactivar(1);

        verify(loteRepository).save(any(Lote.class));
    }

    @Test
    @DisplayName("desactivar: rechaza lote referenciado por movimientos (409)")
    void desactivarConMovimientos() {
        when(loteRepository.findByIdLoteAndActivoTrue(1)).thenReturn(Optional.of(loteGuardado()));
        when(movimientoRepository.countByIdLote(1)).thenReturn(2L);

        assertThatThrownBy(() -> loteService.desactivar(1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("listarActivos: pagina y filtra por material")
    void listarActivosPaginado() {
        when(loteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(loteGuardado()), PageRequest.of(0, 20), 1));

        var resp = loteService.listarActivos(1, "L-2026", 0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).codigoLote()).isEqualTo("L-2026-001");
        assertThat(resp.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("obtener: devuelve 404 si el lote no existe o esta inactivo")
    void obtenerNoEncontrado() {
        when(loteRepository.findByIdLoteAndActivoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> loteService.obtener(99))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("listarLotesDeMaterial: devuelve los lotes de un material activo")
    void listarLotesDeMaterial() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(material));
        when(loteRepository.findAllByActivoTrueAndMaterial_IdMaterialOrderByFechaIngresoDesc(1))
                .thenReturn(List.of(loteGuardado()));

        var resp = loteService.listarLotesDeMaterial(1);

        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).codigoLote()).isEqualTo("L-2026-001");
    }
}