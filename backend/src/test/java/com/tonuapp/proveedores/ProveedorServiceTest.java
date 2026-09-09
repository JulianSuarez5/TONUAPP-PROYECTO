package com.tonuapp.proveedores;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.domain.Material;
import com.tonuapp.domain.MaterialProveedor;
import com.tonuapp.domain.Proveedor;
import com.tonuapp.proveedores.dto.MaterialProveedorRequest;
import com.tonuapp.proveedores.dto.ProveedorRequest;
import com.tonuapp.repository.MaterialProveedorRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.ProveedorRepository;
import com.tonuapp.shared.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class ProveedorServiceTest {

    private ProveedorRepository proveedorRepository;
    private MaterialProveedorRepository materialProveedorRepository;
    private MaterialRepository materialRepository;
    private ProveedorService proveedorService;

    @BeforeEach
    void setUp() {
        proveedorRepository = mock(ProveedorRepository.class);
        materialProveedorRepository = mock(MaterialProveedorRepository.class);
        materialRepository = mock(MaterialRepository.class);
        proveedorService = new ProveedorService(proveedorRepository, materialProveedorRepository, materialRepository);
    }

    private ProveedorRequest request() {
        return new ProveedorRequest("Tienda Agro", "900123456", "Juan", "5551234", "Antioquia");
    }

    private Proveedor proveedorGuardado() {
        Proveedor p = new Proveedor();
        p.setIdProveedor(1);
        p.setNombre("Tienda Agro");
        p.setNit("900123456");
        p.setContacto("Juan");
        p.setTelefono("5551234");
        p.setUbicacion("Antioquia");
        p.setActivo(true);
        p.setFechaRegistro(LocalDateTime.now());
        return p;
    }

    private Material materialActivo() {
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Arena de rio");
        m.setActivo(true);
        return m;
    }

    @Test
    @DisplayName("crear: proveedor valido se guarda y devuelve response")
    void crearValido() {
        when(proveedorRepository.existsByNit("900123456")).thenReturn(false);
        when(proveedorRepository.save(any(Proveedor.class))).thenReturn(proveedorGuardado());

        var resp = proveedorService.crear(request());

        assertThat(resp.nombre()).isEqualTo("Tienda Agro");
        assertThat(resp.nit()).isEqualTo("900123456");
        assertThat(resp.contacto()).isEqualTo("Juan");
        assertThat(resp.cantidadMateriales()).isZero();
    }

    @Test
    @DisplayName("crear: rechaza NIT duplicado (RF-010)")
    void crearNitDuplicado() {
        when(proveedorRepository.existsByNit("900123456")).thenReturn(true);

        assertThatThrownBy(() -> proveedorService.crear(request()))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("obtener: devuelve 404 si el proveedor no existe o esta inactivo")
    void obtenerNoEncontrado() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proveedorService.obtener(99))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("actualizar: proveedor existente se actualiza")
    void actualizarValido() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(proveedorRepository.existsByNit("900123456")).thenReturn(false);
        when(materialProveedorRepository.countByProveedorId(1)).thenReturn(2L);
        when(proveedorRepository.save(any(Proveedor.class))).thenReturn(proveedorGuardado());

        var resp = proveedorService.actualizar(1, request());

        assertThat(resp.nombre()).isEqualTo("Tienda Agro");
        assertThat(resp.cantidadMateriales()).isEqualTo(2L);
    }

    @Test
    @DisplayName("actualizar: permite conservar el NIT (el duplicado es contra otros)")
    void actualizarMismoNit() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(proveedorRepository.existsByNit("900123456")).thenReturn(true);
        when(materialProveedorRepository.countByProveedorId(1)).thenReturn(0L);
        when(proveedorRepository.save(any(Proveedor.class))).thenReturn(proveedorGuardado());

        var resp = proveedorService.actualizar(1, request());

        assertThat(resp.nit()).isEqualTo("900123456");
    }

@Test
    @DisplayName("actualizar: rechaza NIT de otro proveedor")
    void actualizarNitDeOtro() {
        Proveedor p = proveedorGuardado();
        p.setNit("111");
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(p));
        when(proveedorRepository.existsByNit("900123456")).thenReturn(true);

        assertThatThrownBy(() -> proveedorService.actualizar(1, request()))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("desactivar: soft delete cuando no tiene materiales asociados")
    void desactivarValido() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialProveedorRepository.countByProveedorId(1)).thenReturn(0L);
        when(proveedorRepository.save(any(Proveedor.class))).thenReturn(proveedorGuardado());

        proveedorService.desactivar(1);

        verify(proveedorRepository).save(any(Proveedor.class));
    }

    @Test
    @DisplayName("desactivar: rechaza proveedor con materiales asociados (RF-010)")
    void desactivarConMateriales() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialProveedorRepository.countByProveedorId(1)).thenReturn(3L);

        assertThatThrownBy(() -> proveedorService.desactivar(1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("asociarMaterial: valida proveedor/material y guarda la fila N:M")
    void asociarValido() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialActivo()));
        when(materialProveedorRepository.existsByProveedorIdAndMaterialId(1, 1)).thenReturn(false);
        when(materialProveedorRepository.save(any(MaterialProveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = proveedorService.asociarMaterial(1, new MaterialProveedorRequest(1, true));

        assertThat(resp.idMaterial()).isEqualTo(1);
        assertThat(resp.nombreMaterial()).isEqualTo("Arena de rio");
        assertThat(resp.nombreProveedor()).isEqualTo("Tienda Agro");
        assertThat(resp.esPrincipal()).isTrue();
        verify(materialProveedorRepository).findByMaterialIdAndEsPrincipalTrue(1);
    }

    @Test
    @DisplayName("asociarMaterial: rechaza asociacion ya existente")
    void asociarYaExistente() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialActivo()));
        when(materialProveedorRepository.existsByProveedorIdAndMaterialId(1, 1)).thenReturn(true);

        assertThatThrownBy(() -> proveedorService.asociarMaterial(1, new MaterialProveedorRequest(1, false)))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("asociarMaterial: rechaza proveedor inexistente")
    void asociarProveedorInexistente() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proveedorService.asociarMaterial(99, new MaterialProveedorRequest(1, false)))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("actualizarPrincipal: pasa a true y desmarca el anterior")
    void actualizarPrincipalTrue() {
        MaterialProveedor fila = new MaterialProveedor();
        fila.setMaterialId(1);
        fila.setProveedorId(1);
        fila.setEsPrincipal(false);
        fila.setFechaAsociacion(LocalDateTime.now());

        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialActivo()));
        when(materialProveedorRepository.findByProveedorIdAndMaterialId(1, 1)).thenReturn(Optional.of(fila));
        when(materialProveedorRepository.findByMaterialIdAndEsPrincipalTrue(1)).thenReturn(List.of());
        when(materialProveedorRepository.save(any(MaterialProveedor.class))).thenReturn(fila);

        var resp = proveedorService.actualizarPrincipal(1, 1, true);

        assertThat(resp.esPrincipal()).isTrue();
    }

    @Test
    @DisplayName("actualizarPrincipal: rechaza asociacion inexistente")
    void actualizarPrincipalInexistente() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialActivo()));
        when(materialProveedorRepository.findByProveedorIdAndMaterialId(1, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proveedorService.actualizarPrincipal(1, 1, true))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("desasociarMaterial: elimina la fila N:M existente")
    void desasociarValido() {
        MaterialProveedor fila = new MaterialProveedor();
        fila.setMaterialId(1);
        fila.setProveedorId(1);

        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialActivo()));
        when(materialProveedorRepository.findByProveedorIdAndMaterialId(1, 1)).thenReturn(Optional.of(fila));

        proveedorService.desasociarMaterial(1, 1);

        verify(materialProveedorRepository).delete(fila);
    }

    @Test
    @DisplayName("desasociarMaterial: rechaza asociacion inexistente")
    void desasociarInexistente() {
        when(proveedorRepository.findByIdProveedorAndActivoTrue(1)).thenReturn(Optional.of(proveedorGuardado()));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialActivo()));
        when(materialProveedorRepository.findByProveedorIdAndMaterialId(1, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> proveedorService.desasociarMaterial(1, 1))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("listarActivos: pagina y agrega el conteo de materiales por proveedor")
    void listarConConteo() {
        when(proveedorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proveedorGuardado()), PageRequest.of(0, 20), 1));
        when(materialProveedorRepository.findByProveedorIdIn(any()))
                .thenReturn(List.of(fila(1, 10), fila(1, 20), fila(2, 30)));

        var resp = proveedorService.listarActivos(null, 0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).cantidadMateriales()).isEqualTo(2L);
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.page()).isZero();
    }

    @Test
    @DisplayName("listarActivos: filtra por nombre o NIT (contiene, sin distinguir mayusculas)")
    void listarConFiltroQ() {
        when(proveedorRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(proveedorGuardado()), PageRequest.of(0, 20), 1));
        when(materialProveedorRepository.findByProveedorIdIn(any())).thenReturn(List.of());

        var resp = proveedorService.listarActivos("agro", 0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.content().get(0).nombre()).isEqualTo("Tienda Agro");
        verify(proveedorRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listarActivos: 'page' negativo o 'size' fuera de 1..MAX_PAGE_SIZE -> 400")
    void listarPaginacionInvalida() {
        assertThatThrownBy(() -> proveedorService.listarActivos(null, -1, 20))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> proveedorService.listarActivos(null, 0, 0))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> proveedorService.listarActivos(null, 0, 101))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private MaterialProveedor fila(Integer proveedorId, Integer materialId) {
        MaterialProveedor fila = new MaterialProveedor();
        fila.setProveedorId(proveedorId);
        fila.setMaterialId(materialId);
        return fila;
    }
}