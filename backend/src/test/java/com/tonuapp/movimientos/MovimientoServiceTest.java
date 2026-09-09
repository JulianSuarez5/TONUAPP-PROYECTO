package com.tonuapp.movimientos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.alertas.AlertaService;
import com.tonuapp.domain.AjusteInventario;
import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.domain.TipoMovimiento;
import com.tonuapp.domain.Usuario;
import com.tonuapp.movimientos.dto.AjusteRequest;
import com.tonuapp.movimientos.dto.MovimientoRequest;
import com.tonuapp.repository.AjusteInventarioRepository;
import com.tonuapp.repository.LoteRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.UsuarioRepository;
import com.tonuapp.repository.ZonaAcopioRepository;
import com.tonuapp.security.AuthenticatedUser;
import com.tonuapp.shared.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

class MovimientoServiceTest {

    private MovimientoInventarioRepository movimientoRepository;
    private MaterialRepository materialRepository;
    private UsuarioRepository usuarioRepository;
    private AjusteInventarioRepository ajusteRepository;
    private LoteRepository loteRepository;
    private ZonaAcopioRepository zonaRepository;
    private AlertaService alertaService;
    private MovimientoService movimientoService;

    @BeforeEach
    void setUp() {
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        materialRepository = mock(MaterialRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        ajusteRepository = mock(AjusteInventarioRepository.class);
        loteRepository = mock(LoteRepository.class);
        zonaRepository = mock(ZonaAcopioRepository.class);
        alertaService = mock(AlertaService.class);
        movimientoService = new MovimientoService(movimientoRepository, materialRepository,
                usuarioRepository, ajusteRepository, loteRepository, zonaRepository, alertaService);

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(1, "admin.prueba@tonusco.test", "Administrador"), null));
        when(usuarioRepository.getReferenceById(1)).thenReturn(usuario());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MovimientoRequest entrada(String cantidad) {
        return new MovimientoRequest(1, TipoMovimiento.entrada, new BigDecimal(cantidad), null, null, null, null);
    }

    private MovimientoRequest salida(String cantidad) {
        return new MovimientoRequest(1, TipoMovimiento.salida_venta, new BigDecimal(cantidad), "venta", null, null, null);
    }

    private MovimientoRequest merma(String cantidad) {
        return new MovimientoRequest(1, TipoMovimiento.salida_merma, new BigDecimal(cantidad), "derrame", null, null, null);
    }

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setIdUsuario(1);
        u.setNombre("Admin Prueba");
        return u;
    }

    private Material materialCon(int stock) {
        Material m = new Material();
        m.setIdMaterial(1);
        m.setNombre("Cemento");
        m.setStock(new BigDecimal(stock));
        m.setActivo(true);
        return m;
    }

    private MovimientoInventario movGuardado(Material m, TipoMovimiento tipo, int cantidad, EstadoMovimiento estado) {
        MovimientoInventario mov = new MovimientoInventario();
        mov.setIdMovimiento(7);
        mov.setMaterial(m);
        mov.setUsuario(usuario());
        mov.setTipoMovimiento(tipo);
        mov.setCantidad(new BigDecimal(cantidad));
        mov.setMotivo("venta");
        mov.setEstado(estado);
        mov.setFechaMovimiento(LocalDateTime.now());
        return mov;
    }

    @Test
    @DisplayName("entrada: suma al stock y registra movimiento activo")
    void entradaValida() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(7);
            return mov;
        });

        var resp = movimientoService.registrarMovimiento(entrada("5"));

        assertThat(resp.cantidad()).isEqualByComparingTo("5");
        assertThat(resp.tipo()).isEqualTo(TipoMovimiento.entrada);
        assertThat(resp.estado()).isEqualTo(EstadoMovimiento.activo);
        assertThat(m.getStock()).isEqualByComparingTo("15");
        assertThat(resp.idUsuario()).isEqualTo(1);
        verify(movimientoRepository).save(any(MovimientoInventario.class));
    }

    @Test
    @DisplayName("salida_venta: resta del stock")
    void salidaValida() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(7);
            return mov;
        });

        var resp = movimientoService.registrarMovimiento(salida("3"));

        assertThat(m.getStock()).isEqualByComparingTo("7");
        assertThat(resp.tipo()).isEqualTo(TipoMovimiento.salida_venta);
    }

    @Test
    @DisplayName("salida_venta: rechaza cuando supera el stock (409)")
    void salidaSuperiorAlStock() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(salida("11")))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("merma valida: resta del stock")
    void mermaValida() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(7);
            return mov;
        });

        movimientoService.registrarMovimiento(merma("4"));

        assertThat(m.getStock()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("merma: rechaza cuando supera el stock (RF-013, 409)")
    void mermaSuperiorAlStock() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(merma("10.5")))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("registrar: tipo ajuste en endpoint normal -> 400 (va por /ajuste)")
    void ajusteEnRutaNormalRechazado() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialCon(10)));

        MovimientoRequest req = new MovimientoRequest(1, TipoMovimiento.ajuste, new BigDecimal("5"), "x", null, null, null);

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("registrar: cantidad menor o igual a 0 -> 400")
    void cantidadCeroRechazada() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));

        MovimientoRequest req = new MovimientoRequest(1, TipoMovimiento.entrada, BigDecimal.ZERO, null, null, null, null);

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("registrar: material inexistente -> 404")
    void materialInexistente() {
        when(materialRepository.findByIdMaterialAndActivoTrue(999)).thenReturn(Optional.empty());

        MovimientoRequest req = new MovimientoRequest(999, TipoMovimiento.entrada, new BigDecimal("5"), null, null, null, null);

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("registrar: referencia a lote inexistente -> 404 (RF-015)")
    void loteInexistente() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialCon(10)));
        when(loteRepository.findByIdLoteAndActivoTrue(99)).thenReturn(Optional.empty());

        MovimientoRequest req = new MovimientoRequest(1, TipoMovimiento.entrada, new BigDecimal("5"), null, null, 99, null);

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("registrar: referencia a zona inexistente -> 404 (RF-015)")
    void zonaInexistente() {
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(materialCon(10)));
        when(zonaRepository.findByIdZonaAndActivoTrue(77)).thenReturn(Optional.empty());

        MovimientoRequest req = new MovimientoRequest(1, TipoMovimiento.entrada, new BigDecimal("5"), null, null, null, 77);

        assertThatThrownBy(() -> movimientoService.registrarMovimiento(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("registrar: lote y zona validos se guardan en el movimiento (RF-015)")
    void loteYZonaValidos() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(loteRepository.findByIdLoteAndActivoTrue(99)).thenReturn(Optional.of(mock(com.tonuapp.domain.Lote.class)));
        when(zonaRepository.findByIdZonaAndActivoTrue(77)).thenReturn(Optional.of(mock(com.tonuapp.domain.ZonaAcopio.class)));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(7);
            return mov;
        });

        MovimientoRequest req = new MovimientoRequest(1, TipoMovimiento.entrada, new BigDecimal("5"), null, null, 99, 77);

        var resp = movimientoService.registrarMovimiento(req);

        assertThat(resp.idLote()).isEqualTo(99);
        assertThat(resp.idZona()).isEqualTo(77);
        assertThat(m.getStock()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("ajuste: fija el stock a la cifra contada (sube y guarda |delta|)")
    void ajusteAlAlza() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(9);
            return mov;
        });

        var resp = movimientoService.registrarAjuste(new AjusteRequest(1, new BigDecimal("25"), "conteo fisico"));

        assertThat(m.getStock()).isEqualByComparingTo("25");
        assertThat(resp.cantidad()).isEqualByComparingTo("15");
        assertThat(resp.tipo()).isEqualTo(TipoMovimiento.ajuste);
        assertThat(resp.motivo()).isEqualTo("conteo fisico");
    }

    @Test
    @DisplayName("ajuste: guarda conciliacion en ajustes_inventario (anterior/nueva)")
    void ajusteGuardaConciliacion() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(9);
            return mov;
        });
        when(ajusteRepository.save(any(AjusteInventario.class))).thenAnswer(inv -> inv.getArgument(0));

        movimientoService.registrarAjuste(new AjusteRequest(1, new BigDecimal("25"), "conteo fisico"));

        verify(ajusteRepository).save(any(AjusteInventario.class));
    }

    @Test
    @DisplayName("ajuste: baja del stock sin quedar negativo")
    void ajusteALaBaja() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> {
            MovimientoInventario mov = inv.getArgument(0);
            mov.setIdMovimiento(9);
            return mov;
        });

        var resp = movimientoService.registrarAjuste(new AjusteRequest(1, new BigDecimal("3"), "reclasificacion"));

        assertThat(m.getStock()).isEqualByComparingTo("3");
        assertThat(resp.cantidad()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("ajuste: sin diferencia -> 400")
    void ajusteSinDiferencia() {
        Material m = materialCon(10);
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> movimientoService.registrarAjuste(new AjusteRequest(1, new BigDecimal("10"), "igual")))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("salida: si deja bajo el minimo dispara la evaluacion de alerta (RF-012)")
    void salidaDisparaEvaluacionAlerta() {
        Material m = materialCon(10);
        m.setStockMinimo(new BigDecimal("12"));
        when(materialRepository.findByIdMaterialAndActivoTrue(1)).thenReturn(Optional.of(m));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenAnswer(inv -> inv.getArgument(0));

        movimientoService.registrarMovimiento(salida("3"));

        assertThat(m.getStock()).isEqualByComparingTo("7");
        verify(alertaService).generarSiCorresponde(m);
    }

    @Test
    @DisplayName("anular entrada: restaura el stock")
    void anularEntrada() {
        Material m = materialCon(15);
        MovimientoInventario mov = movGuardado(m, TipoMovimiento.entrada, 5, EstadoMovimiento.activo);
        when(movimientoRepository.findById(7)).thenReturn(Optional.of(mov));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenReturn(mov);

        var resp = movimientoService.anular(7);

        assertThat(resp.estado()).isEqualTo(EstadoMovimiento.anulado);
        assertThat(m.getStock()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("anular salida: restaura el stock")
    void anularSalida() {
        Material m = materialCon(7);
        MovimientoInventario mov = movGuardado(m, TipoMovimiento.salida_venta, 3, EstadoMovimiento.activo);
        when(movimientoRepository.findById(7)).thenReturn(Optional.of(mov));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenReturn(mov);

        movimientoService.anular(7);

        assertThat(m.getStock()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("anular: movimiento inexistente -> 404")
    void anularInexistente() {
        when(movimientoRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movimientoService.anular(999))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("anular: ya anulado -> 409")
    void anularYaAnulado() {
        Material m = materialCon(10);
        MovimientoInventario mov = movGuardado(m, TipoMovimiento.entrada, 5, EstadoMovimiento.anulado);
        when(movimientoRepository.findById(7)).thenReturn(Optional.of(mov));

        assertThatThrownBy(() -> movimientoService.anular(7))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("anular ajuste: revierte el stock a la cantidad anterior (ajustes_inventario)")
    void anularAjusteRestauraAnterior() {
        Material m = materialCon(25);
        MovimientoInventario mov = movGuardado(m, TipoMovimiento.ajuste, 15, EstadoMovimiento.activo);
        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setIdAjuste(1);
        ajuste.setMovimiento(mov);
        ajuste.setCantidadAnterior(new BigDecimal("10"));
        ajuste.setCantidadNueva(new BigDecimal("25"));

        when(movimientoRepository.findById(7)).thenReturn(Optional.of(mov));
        when(ajusteRepository.findByMovimiento_IdMovimiento(7)).thenReturn(Optional.of(ajuste));
        when(movimientoRepository.save(any(MovimientoInventario.class))).thenReturn(mov);

        var resp = movimientoService.anular(7);

        assertThat(resp.estado()).isEqualTo(EstadoMovimiento.anulado);
        assertThat(resp.tipo()).isEqualTo(TipoMovimiento.ajuste);
        assertThat(m.getStock()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("anular ajuste: sin detalle en ajustes_inventario -> 409")
    void anularAjusteSinDetalle() {
        Material m = materialCon(25);
        MovimientoInventario mov = movGuardado(m, TipoMovimiento.ajuste, 15, EstadoMovimiento.activo);
        when(movimientoRepository.findById(7)).thenReturn(Optional.of(mov));
        when(ajusteRepository.findByMovimiento_IdMovimiento(7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movimientoService.anular(7))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("anular: rechaza si revertir dejaria el stock en negativo")
    void anularDejaNegativo() {
        Material m = materialCon(2);
        MovimientoInventario mov = movGuardado(m, TipoMovimiento.entrada, 5, EstadoMovimiento.activo);
        when(movimientoRepository.findById(7)).thenReturn(Optional.of(mov));

        assertThatThrownBy(() -> movimientoService.anular(7))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("listar: pagina con filtros (material/tipo/estado/rango) y devuelve metadatos")
    void listarConFiltros() {
        Material m = materialCon(10);
        List<MovimientoInventario> result = List.of(movGuardado(m, TipoMovimiento.entrada, 5, EstadoMovimiento.activo));
        when(movimientoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(result, PageRequest.of(0, 20), 1));

        var resp = movimientoService.listar(1, TipoMovimiento.entrada, EstadoMovimiento.activo,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 0, 20);

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.totalPages()).isEqualTo(1);
        assertThat(resp.page()).isZero();
        assertThat(resp.size()).isEqualTo(20);
        verify(movimientoRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("listar: con filtros todos nulos consulta todo el historico paginado")
    void listarSinFiltros() {
        when(movimientoRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var resp = movimientoService.listar(null, null, null, null, null, 0, 20);

        assertThat(resp.content()).isEmpty();
        assertThat(resp.totalElements()).isZero();
    }

    @Test
    @DisplayName("listar: 'desde' posterior a 'hasta' -> 400")
    void listarRangoInvertido() {
        assertThatThrownBy(() -> movimientoService.listar(null, null, null,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), 0, 20))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("listar: 'page' negativo -> 400")
    void listarPageNegativa() {
        assertThatThrownBy(() -> movimientoService.listar(null, null, null, null, null, -1, 20))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("listar: 'size' fuera de 1..MAX_PAGE_SIZE -> 400")
    void listarSizeInvalido() {
        assertThatThrownBy(() -> movimientoService.listar(null, null, null, null, null, 0, 0))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> movimientoService.listar(null, null, null, null, null, 0, 101))
                .isInstanceOf(ApiException.class)
                .extracting("status", org.assertj.core.api.InstanceOfAssertFactories.type(org.springframework.http.HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}