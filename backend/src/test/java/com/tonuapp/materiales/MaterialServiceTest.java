package com.tonuapp.materiales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tonuapp.domain.Categoria;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.UnidadMedida;
import com.tonuapp.materiales.dto.MaterialRequest;
import com.tonuapp.repository.CategoriaRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.UnidadMedidaRepository;
import com.tonuapp.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class MaterialServiceTest {

    private MaterialRepository materialRepository;
    private CategoriaRepository categoriaRepository;
    private UnidadMedidaRepository unidadMedidaRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private MaterialService materialService;

    private Categoria categoria;
    private UnidadMedida unidad;

    @BeforeEach
    void setUp() {
        materialRepository = mock(MaterialRepository.class);
        categoriaRepository = mock(CategoriaRepository.class);
        unidadMedidaRepository = mock(UnidadMedidaRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        materialService = new MaterialService(materialRepository, categoriaRepository,
                unidadMedidaRepository, movimientoRepository);

        categoria = new Categoria();
        categoria.setIdCategoria(1);
        categoria.setNombre("Arena");
        categoria.setActivo(true);

        unidad = new UnidadMedida();
        unidad.setIdUnidad(1);
        unidad.setNombre("Metro cubico");
        unidad.setAbreviatura("m3");
    }

    private MaterialRequest request() {
        return new MaterialRequest("Arena de rio", 1, 1, new BigDecimal("10.00"), new BigDecimal("5.00"));
    }

    private Material materialGuardado() {
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Arena de rio");
        m.setCategoria(categoria);
        m.setUnidad(unidad);
        m.setStock(new BigDecimal("10.00"));
        m.setStockMinimo(new BigDecimal("5.00"));
        m.setActivo(true);
        m.setFechaRegistro(LocalDateTime.now());
        return m;
    }

    @Test
    @DisplayName("crear: material valido se guarda y devuelve response")
    void crearValido() {
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(unidadMedidaRepository.findById(1)).thenReturn(Optional.of(unidad));
        when(materialRepository.existsByNombreAndCategoria_IdCategoria("Arena de rio", 1)).thenReturn(false);
        when(materialRepository.save(any(Material.class))).thenReturn(materialGuardado());

        var resp = materialService.crear(request());

        assertThat(resp.nombre()).isEqualTo("Arena de rio");
        assertThat(resp.stock()).isEqualByComparingTo("10.00");
        assertThat(resp.stockMinimo()).isEqualByComparingTo("5.00");
        assertThat(resp.categoriaNombre()).isEqualTo("Arena");
        assertThat(resp.unidadAbreviatura()).isEqualTo("m3");
    }

    @Test
    @DisplayName("crear: rechaza nombre duplicado en la misma categoria")
    void crearDuplicado() {
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(materialRepository.existsByNombreAndCategoria_IdCategoria("Arena de rio", 1)).thenReturn(true);

        assertThatThrownBy(() -> materialService.crear(request()))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("crear: rechaza categoria inexistente o inactiva")
    void crearCategoriaInvalida() {
        when(categoriaRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.crear(request()))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("crear: rechaza unidad de medida inexistente")
    void crearUnidadInvalida() {
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(unidadMedidaRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.crear(request()))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("actualizar: material existente se actualiza")
    void actualizarValido() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialGuardado()));
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(unidadMedidaRepository.findById(1)).thenReturn(Optional.of(unidad));
        when(materialRepository.existsByNombreAndCategoria_IdCategoria("Arena de rio", 1)).thenReturn(false);
        when(materialRepository.save(any(Material.class))).thenReturn(materialGuardado());

        var resp = materialService.actualizar(1, request());

        assertThat(resp.nombre()).isEqualTo("Arena de rio");
    }

    @Test
    @DisplayName("actualizar: permite conservar el nombre (el duplicado es contra otros)")
    void actualizarMismoNombre() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialGuardado()));
        when(categoriaRepository.findById(1)).thenReturn(Optional.of(categoria));
        when(unidadMedidaRepository.findById(1)).thenReturn(Optional.of(unidad));
        when(materialRepository.existsByNombreAndCategoria_IdCategoria("Arena de rio", 1)).thenReturn(true);
        when(materialRepository.save(any(Material.class))).thenReturn(materialGuardado());

        var resp = materialService.actualizar(1, request());

        assertThat(resp.nombre()).isEqualTo("Arena de rio");
    }

    @Test
    @DisplayName("obtener: devuelve 404 si el material no existe o esta inactivo")
    void obtenerNoEncontrado() {
        when(materialRepository.findByIdMaterialAndActivoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> materialService.obtener(99))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("buscar: exige al menos un filtro (RF-003)")
    void buscarSinFiltros() {
        assertThatThrownBy(() -> materialService.buscar(null, null, 0, 20))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("buscar: consulta con nombre y devuelve materiales activos paginados")
    void buscarPorNombre() {
        when(materialRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(materialGuardado()), PageRequest.of(0, 20), 1));

        var resp = materialService.buscar("arena", null, 0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).nombre()).isEqualTo("Arena de rio");
        assertThat(resp.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("buscar: 'size' fuera de 1..MAX_PAGE_SIZE -> 400")
    void buscarSizeInvalido() {
        assertThatThrownBy(() -> materialService.buscar("arena", null, 0, 0))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("desactivar: soft delete cuando no hay movimientos")
    void desactivarValido() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialGuardado()));
        when(movimientoRepository.countByMaterial_IdMaterial(1)).thenReturn(0L);
        when(materialRepository.save(any(Material.class))).thenReturn(materialGuardado());

        materialService.desactivar(1);

        org.mockito.Mockito.verify(materialRepository).save(any(Material.class));
    }

    @Test
    @DisplayName("desactivar: rechaza material con movimientos registrados (RF-004)")
    void desactivarConMovimientos() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialGuardado()));
        when(movimientoRepository.countByMaterial_IdMaterial(1)).thenReturn(3L);

        assertThatThrownBy(() -> materialService.desactivar(1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("listarActivos: pagina y devuelve contenido y metadatos")
    void listarActivosPaginado() {
        when(materialRepository.findAllByActivoTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(materialGuardado()), PageRequest.of(0, 20), 1));

        var resp = materialService.listarActivos(0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).nombre()).isEqualTo("Arena de rio");
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.totalPages()).isEqualTo(1);
        assertThat(resp.page()).isZero();
        assertThat(resp.size()).isEqualTo(20);
        org.mockito.Mockito.verify(materialRepository).findAllByActivoTrue(any(Pageable.class));
    }

    @Test
    @DisplayName("listarActivos: 'page' negativo -> 400")
    void listarActivosPageNegativa() {
        assertThatThrownBy(() -> materialService.listarActivos(-1, 20))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("listarActivos: 'size' fuera de 1..MAX_PAGE_SIZE -> 400")
    void listarActivosSizeInvalido() {
        assertThatThrownBy(() -> materialService.listarActivos(0, 0))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> materialService.listarActivos(0, 101))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}