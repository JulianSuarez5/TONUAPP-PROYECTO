package com.tonuapp.ubicaciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.domain.Categoria;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.UnidadMedida;
import com.tonuapp.domain.ZonaAcopio;
import com.tonuapp.materiales.dto.MaterialResponse;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.ZonaAcopioRepository;
import com.tonuapp.shared.ApiException;
import com.tonuapp.ubicaciones.dto.ZonaAcopioRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

class ZonaAcopioServiceTest {

    private ZonaAcopioRepository zonaRepository;
    private MaterialRepository materialRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private ZonaAcopioService zonaService;

    private ZonaAcopio zonaPatio;

    @BeforeEach
    void setUp() {
        zonaRepository = mock(ZonaAcopioRepository.class);
        materialRepository = mock(MaterialRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        zonaService = new ZonaAcopioService(zonaRepository, materialRepository, movimientoRepository);

        zonaPatio = new ZonaAcopio();
        zonaPatio.setIdZona(1);
        zonaPatio.setNombreZona("Patio Sur");
        zonaPatio.setCapacidadMaxima(new BigDecimal("500.00"));
        zonaPatio.setTipoMaterialPermitido("Arena");
        zonaPatio.setActivo(true);
    }

    private ZonaAcopioRequest request(String nombre, String tipo) {
        return new ZonaAcopioRequest(nombre, new BigDecimal("500.00"), tipo);
    }

    private Categoria categoriaArena() {
        Categoria c = new Categoria();
        c.setIdCategoria(1);
        c.setNombre("Arena");
        return c;
    }

    private Material materialArena() {
        Categoria c = new Categoria();
        c.setIdCategoria(1);
        c.setNombre("Arena");
        UnidadMedida u = new UnidadMedida();
        u.setIdUnidad(3);
        u.setNombre("Metro cubico");
        u.setAbreviatura("m3");
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Arena de rio");
        m.setCategoria(c);
        m.setUnidad(u);
        m.setActivo(true);
        return m;
    }

    @Test
    @DisplayName("listarActivas: pagina y devuelve conteo de materiales por zona")
    void listarActivasPaginado() {
        when(zonaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(zonaPatio), PageRequest.of(0, 20), 1));
        Material asignado = materialArena();
        asignado.setZona(zonaPatio);
        when(materialRepository.findAllByActivoTrueAndZona_IdZonaIn(any())).thenReturn(List.of(asignado));

        var resp = zonaService.listarActivas(null, 0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).nombreZona()).isEqualTo("Patio Sur");
        assertThat(resp.content().get(0).cantidadMateriales()).isEqualTo(1);
        assertThat(resp.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("listarActivas: 'size' fuera de rango -> 400")
    void listarActivasSizeInvalido() {
        assertThatThrownBy(() -> zonaService.listarActivas(null, 0, 0))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("obtener: devuelve 404 si la zona no existe o esta inactiva")
    void obtenerNoEncontrada() {
        when(zonaRepository.findByIdZonaAndActivoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> zonaService.obtener(99))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("crear: zona valida se guarda y devuelve response")
    void crearValido() {
        when(zonaRepository.existsByNombreZonaIgnoreCase("Patio Este")).thenReturn(false);
        when(zonaRepository.save(any(ZonaAcopio.class))).thenAnswer(inv -> {
            ZonaAcopio z = inv.getArgument(0);
            z.setIdZona(2);
            return z;
        });

        var resp = zonaService.crear(request("Patio Este", "Gravilla"));

        assertThat(resp.idZona()).isEqualTo(2);
        assertThat(resp.nombreZona()).isEqualTo("Patio Este");
        assertThat(resp.tipoMaterialPermitido()).isEqualTo("Gravilla");
        assertThat(resp.activo()).isTrue();
        assertThat(resp.cantidadMateriales()).isZero();
    }

    @Test
    @DisplayName("crear: rechaza nombre duplicado (case-insensitive, 409)")
    void crearNombreDuplicado() {
        when(zonaRepository.existsByNombreZonaIgnoreCase("patio sur")).thenReturn(true);

        assertThatThrownBy(() -> zonaService.crear(request("patio sur", null)))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("actualizar: permite conservar el nombre y cambia capacidad/tipo")
    void actualizarValido() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        when(zonaRepository.existsByNombreZonaIgnoreCaseAndIdZonaNot("Patio Sur", 1)).thenReturn(false);
        when(zonaRepository.save(any(ZonaAcopio.class))).thenReturn(zonaPatio);

        var resp = zonaService.actualizar(1, request("Patio Sur", "Gravilla"));

        assertThat(resp.capacidadMaxima()).isEqualByComparingTo("500.00");
        assertThat(resp.tipoMaterialPermitido()).isEqualTo("Gravilla");
    }

    @Test
    @DisplayName("actualizar: rechaza nombre usado por otra zona (409)")
    void actualizarNombreAjeno() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        when(zonaRepository.existsByNombreZonaIgnoreCaseAndIdZonaNot("Patio Norte", 1)).thenReturn(true);

        assertThatThrownBy(() -> zonaService.actualizar(1, request("Patio Norte", null)))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("actualizar: cambiar el tipo con materiales incompatibles -> 409 (RF-015)")
    void actualizarTipoIncompatible() {
        ZonaAcopio z = new ZonaAcopio();
        z.setIdZona(1);
        z.setNombreZona("Patio Sur");
        z.setCapacidadMaxima(new BigDecimal("500.00"));
        z.setTipoMaterialPermitido(null);
        z.setActivo(true);
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(z));
        when(zonaRepository.existsByNombreZonaIgnoreCaseAndIdZonaNot("Patio Sur", 1)).thenReturn(false);

        Material m = materialArena();
        m.setZona(z);
        when(materialRepository.findAllByActivoTrueAndZona_IdZona(1)).thenReturn(List.of(m));

        assertThatThrownBy(() -> zonaService.actualizar(1, request("Patio Sur", "Cemento")))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("desactivar: soft delete cuando no tiene materiales ni movimientos")
    void desactivarValido() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        when(materialRepository.findAllByActivoTrueAndZona_IdZona(1)).thenReturn(List.of());
        when(movimientoRepository.countByIdZona(1)).thenReturn(0L);

        zonaService.desactivar(1);

        verify(zonaRepository).save(any(ZonaAcopio.class));
    }

    @Test
    @DisplayName("desactivar: rechaza zona con materiales asignados (RF-015, 409)")
    void desactivarConMateriales() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        when(materialRepository.findAllByActivoTrueAndZona_IdZona(1)).thenReturn(List.of(materialArena()));

        assertThatThrownBy(() -> zonaService.desactivar(1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("desactivar: rechaza zona referenciada por movimientos (409)")
    void desactivarConMovimientos() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        when(materialRepository.findAllByActivoTrueAndZona_IdZona(1)).thenReturn(List.of());
        when(movimientoRepository.countByIdZona(1)).thenReturn(4L);

        assertThatThrownBy(() -> zonaService.desactivar(1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("asignarMaterial: material compatible se asigna a la zona")
    void asignarMaterialCompatible() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        Material m = materialArena();
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(materialRepository.save(any(Material.class))).thenReturn(m);

        MaterialResponse resp = zonaService.asignarMaterial(1, 1);

        assertThat(resp.idZona()).isEqualTo(1);
        assertThat(resp.zonaNombre()).isEqualTo("Patio Sur");
    }

    @Test
    @DisplayName("asignarMaterial: material incompatible con el tipo de la zona -> 409 (RF-015)")
    void asignarMaterialIncompatible() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        Material m = new Material();
        m.setIdMaterial(2);
        m.setNombre("Cemento Gris");
        Categoria cemento = new Categoria();
        cemento.setIdCategoria(3);
        cemento.setNombre("Cemento");
        m.setCategoria(cemento);
        m.setActivo(true);
        when(materialRepository.findByIdMaterialAndActivoTrue(2)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> zonaService.asignarMaterial(1, 2))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("asignarMaterial: material ya asignado a esa zona -> 409")
    void asignarMaterialYaAsignado() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        Material m = materialArena();
        m.setZona(zonaPatio);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> zonaService.asignarMaterial(1, 1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("desasignarMaterial: deja el material sin zona")
    void desasignarMaterialValido() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        Material m = materialArena();
        m.setZona(zonaPatio);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(materialRepository.save(any(Material.class))).thenReturn(m);

        MaterialResponse resp = zonaService.desasignarMaterial(1, 1);

        assertThat(resp.idZona()).isNull();
        assertThat(resp.zonaNombre()).isNull();
    }

    @Test
    @DisplayName("desasignarMaterial: material no asignado a esa zona -> 409")
    void desasignarMaterialNoAsignado() {
        when(zonaRepository.findByIdZonaAndActivoTrue(1)).thenReturn(Optional.of(zonaPatio));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialArena()));

        assertThatThrownBy(() -> zonaService.desasignarMaterial(1, 1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }
}