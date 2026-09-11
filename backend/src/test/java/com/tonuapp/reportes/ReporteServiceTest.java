package com.tonuapp.reportes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tonuapp.audit.AuditService;
import com.tonuapp.config.ReporteProperties;
import com.tonuapp.domain.AjusteInventario;
import com.tonuapp.domain.AlertaEstado;
import com.tonuapp.domain.AlertaInventario;
import com.tonuapp.domain.Categoria;
import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.domain.ReporteGenerado;
import com.tonuapp.domain.TipoMovimiento;
import com.tonuapp.domain.UnidadMedida;
import com.tonuapp.domain.Usuario;
import com.tonuapp.movimientos.dto.MovimientoResponse;
import com.tonuapp.reportes.dto.ReporteGeneradoResponse;
import com.tonuapp.reportes.dto.ReporteInventarioRow;
import com.tonuapp.reportes.dto.ReporteRequest;
import com.tonuapp.reportes.dto.ReporteResumenRow;
import com.tonuapp.reportes.export.ReporteExporter;
import com.tonuapp.repository.AjusteInventarioRepository;
import com.tonuapp.repository.AlertaInventarioRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.ReporteGeneradoRepository;
import com.tonuapp.security.AuthenticatedUser;
import com.tonuapp.shared.ApiException;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class ReporteServiceTest {

    private MaterialRepository materialRepository;
    private MovimientoInventarioRepository movimientoRepository;
    private AjusteInventarioRepository ajusteRepository;
    private AlertaInventarioRepository alertaRepository;
    private ReporteGeneradoRepository reporteRepository;
    private ReporteExporter exporter;
    private AuditService auditService;
    private ReporteProperties properties;
    private ReporteService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        materialRepository = mock(MaterialRepository.class);
        movimientoRepository = mock(MovimientoInventarioRepository.class);
        ajusteRepository = mock(AjusteInventarioRepository.class);
        alertaRepository = mock(AlertaInventarioRepository.class);
        reporteRepository = mock(ReporteGeneradoRepository.class);
        exporter = mock(ReporteExporter.class);
        auditService = mock(AuditService.class);
        properties = new ReporteProperties();
        properties.setDirectorio(tempDir.toString());
        service = new ReporteService(materialRepository, movimientoRepository, ajusteRepository,
                alertaRepository, reporteRepository, exporter, auditService, properties);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(1, "admin.prueba@tonusco.test", "Administrador"),
                        null, List.of()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private Material material(Integer id, String nombre, BigDecimal stock, BigDecimal minimo) {
        Categoria cat = new Categoria();
        cat.setIdCategoria(1);
        cat.setNombre("Arena");
        UnidadMedida unidad = new UnidadMedida();
        unidad.setIdUnidad(1);
        unidad.setNombre("Metro");
        unidad.setAbreviatura("m");
        Material m = new Material();
        m.setIdMaterial(id);
        m.setNombre(nombre);
        m.setCategoria(cat);
        m.setUnidad(unidad);
        m.setStock(stock);
        m.setStockMinimo(minimo);
        m.setActivo(true);
        return m;
    }

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setIdUsuario(1);
        u.setNombre("Admin Prueba");
        return u;
    }

    private MovimientoInventario movimiento(Material m, TipoMovimiento tipo, BigDecimal cantidad,
                                            EstadoMovimiento estado) {
        MovimientoInventario mv = new MovimientoInventario();
        mv.setIdMovimiento(1);
        mv.setMaterial(m);
        mv.setUsuario(usuario());
        mv.setTipoMovimiento(tipo);
        mv.setCantidad(cantidad);
        mv.setEstado(estado);
        mv.setMotivo("test");
        mv.setFechaMovimiento(LocalDateTime.now());
        return mv;
    }

    private AjusteInventario ajuste(Material m, BigDecimal anterior, BigDecimal nueva) {
        AjusteInventario a = new AjusteInventario();
        a.setIdAjuste(1);
        a.setMaterial(m);
        a.setCantidadAnterior(anterior);
        a.setCantidadNueva(nueva);
        a.setFechaAjuste(LocalDateTime.now());
        return a;
    }

    @Test
    @DisplayName("inventario: filas con alerta activa marcada")
    void listarInventario() {
        Material m1 = material(1, "Cemento", BigDecimal.valueOf(5), BigDecimal.valueOf(10));
        Material m2 = material(2, "Arena", BigDecimal.valueOf(20), BigDecimal.valueOf(10));
        when(materialRepository.findAllByActivoTrueOrderByNombreAsc()).thenReturn(List.of(m1, m2));
        AlertaInventario alerta = new AlertaInventario();
        alerta.setMaterial(m1);
        alerta.setEstado(AlertaEstado.activa);
        when(alertaRepository.findAllByEstadoOrderByFechaGeneradaDesc(AlertaEstado.activa))
                .thenReturn(List.of(alerta));

        List<ReporteInventarioRow> filas = service.listarInventario();

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).conAlertaActiva()).isTrue();
        assertThat(filas.get(1).conAlertaActiva()).isFalse();
        assertThat(filas.get(0).categoria()).isEqualTo("Arena");
        assertThat(filas.get(0).unidad()).isEqualTo("Metro");
    }

    @Test
    @DisplayName("bajo-stock: solo materiales bajo el minimo, del mas critico al menos")
    void bajoStock() {
        Material m1 = material(1, "Cemento", BigDecimal.valueOf(5), BigDecimal.valueOf(10));
        Material m2 = material(2, "Arena", BigDecimal.valueOf(20), BigDecimal.valueOf(10));
        when(materialRepository.findAllActivosBajoMinimo()).thenReturn(List.of(m1));

        List<ReporteInventarioRow> filas = service.bajoStock();

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).nombreMaterial()).isEqualTo("Cemento");
    }

    @Test
    @DisplayName("movimientos: respeta el rango inclusive (desde 00:00 hasta 23:59:59)")
    void listarMovimientosRango() {
        LocalDate desde = LocalDate.of(2026, 9, 1);
        LocalDate hasta = LocalDate.of(2026, 9, 5);
        Material m = material(1, "Cemento", BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        when(movimientoRepository.findAllByFechaMovimientoBetweenOrderByFechaMovimientoDesc(
                desde.atStartOfDay(), hasta.atTime(LocalTime.MAX)))
                .thenReturn(List.of(movimiento(m, TipoMovimiento.entrada, BigDecimal.valueOf(10), EstadoMovimiento.activo)));

        List<MovimientoResponse> filas = service.listarMovimientos(desde, hasta);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).nombreMaterial()).isEqualTo("Cemento");
        assertThat(filas.get(0).cantidad()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("movimientos: rango incompleto -> 400")
    void listarMovimientosRangoIncompleto() {
        assertThatThrownBy(() -> service.listarMovimientos(LocalDate.of(2026, 9, 5), null))
                .isInstanceOf(ApiException.class)
                .extracting("status", InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("movimientos: desde posterior a hasta -> 400")
    void listarMovimientosRangoInvertido() {
        assertThatThrownBy(() -> service.listarMovimientos(
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(ApiException.class)
                .extracting("status", InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("resumen: agrega por material incluyendo el delta de los ajustes")
    void resumen() {
        Material m1 = material(1, "Cemento", BigDecimal.valueOf(20), BigDecimal.valueOf(10));
        Material m2 = material(2, "Arena", BigDecimal.valueOf(5), BigDecimal.valueOf(10));
        MovimientoInventario ajusteMov = movimiento(m1, TipoMovimiento.ajuste, BigDecimal.valueOf(9), EstadoMovimiento.activo);
        AjusteInventario detalle = ajuste(m1, BigDecimal.valueOf(11), BigDecimal.valueOf(20));
        detalle.setMovimiento(ajusteMov);
        List<MovimientoInventario> movs = List.of(
                movimiento(m1, TipoMovimiento.entrada, BigDecimal.valueOf(10), EstadoMovimiento.activo),
                movimiento(m1, TipoMovimiento.salida_venta, BigDecimal.valueOf(4), EstadoMovimiento.activo),
                movimiento(m1, TipoMovimiento.salida_merma, BigDecimal.valueOf(1), EstadoMovimiento.activo),
                ajusteMov,
                movimiento(m2, TipoMovimiento.entrada, BigDecimal.valueOf(5), EstadoMovimiento.activo));
        when(movimientoRepository.findAllByOrderByFechaMovimientoDesc()).thenReturn(movs);
        when(ajusteRepository.findAllByOrderByFechaAjusteDesc()).thenReturn(List.of(detalle));

        List<ReporteResumenRow> filas = service.resumen(null, null);

        ReporteResumenRow cemento = filas.stream().filter(f -> f.nombreMaterial().equals("Cemento")).findFirst().orElseThrow();
        ReporteResumenRow arena = filas.stream().filter(f -> f.nombreMaterial().equals("Arena")).findFirst().orElseThrow();
        assertThat(cemento.entradas()).isEqualByComparingTo("10");
        assertThat(cemento.salidasVenta()).isEqualByComparingTo("4");
        assertThat(cemento.salidasMerma()).isEqualByComparingTo("1");
        assertThat(cemento.ajusteNeto()).isEqualByComparingTo("9");
        assertThat(cemento.efectoNeto()).isEqualByComparingTo("14");
        assertThat(cemento.stockActual()).isEqualByComparingTo("20");
        assertThat(arena.entradas()).isEqualByComparingTo("5");
        // ordenados por nombre
        assertThat(filas.get(0).nombreMaterial()).isEqualTo("Arena");
        assertThat(filas.get(1).nombreMaterial()).isEqualTo("Cemento");
    }

    @Test
    @DisplayName("resumen: excluye los movimientos anulados (efecto revertido)")
    void resumenExcluyeAnulados() {
        Material m1 = material(1, "Cemento", BigDecimal.valueOf(10), BigDecimal.valueOf(5));
        when(movimientoRepository.findAllByOrderByFechaMovimientoDesc()).thenReturn(List.of(
                movimiento(m1, TipoMovimiento.entrada, BigDecimal.valueOf(10), EstadoMovimiento.anulado),
                movimiento(m1, TipoMovimiento.entrada, BigDecimal.valueOf(2), EstadoMovimiento.activo)));
        when(ajusteRepository.findAllByOrderByFechaAjusteDesc()).thenReturn(List.of());

        List<ReporteResumenRow> filas = service.resumen(null, null);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).entradas()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("resumen: excluye el delta de un ajuste cuyo movimiento fue anulado (D-19)")
    void resumenExcluyeAjusteAnulado() {
        Material m1 = material(1, "Cemento", BigDecimal.valueOf(30), BigDecimal.valueOf(10));
        MovimientoInventario entrada = movimiento(m1, TipoMovimiento.entrada, BigDecimal.valueOf(15), EstadoMovimiento.activo);
        MovimientoInventario ajusteAnulado = movimiento(m1, TipoMovimiento.ajuste, BigDecimal.valueOf(9), EstadoMovimiento.anulado);
        AjusteInventario detalle = ajuste(m1, BigDecimal.valueOf(20), BigDecimal.valueOf(11));
        detalle.setMovimiento(ajusteAnulado);
        when(movimientoRepository.findAllByOrderByFechaMovimientoDesc()).thenReturn(List.of(ajusteAnulado, entrada));
        when(ajusteRepository.findAllByOrderByFechaAjusteDesc()).thenReturn(List.of(detalle));

        List<ReporteResumenRow> filas = service.resumen(null, null);

        assertThat(filas).hasSize(1);
        ReporteResumenRow r = filas.get(0);
        assertThat(r.entradas()).isEqualByComparingTo("15");
        // el delta (-9) NO suma: solo contarian ajustes con movimiento activo
        assertThat(r.ajusteNeto()).isEqualByComparingTo("0");
        assertThat(r.efectoNeto()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("exportar: genera PDF, guarda archivo, registra bitacora y audita CREATE")
    void exportarPdf() throws Exception {
        Material m1 = material(1, "Cemento", BigDecimal.valueOf(5), BigDecimal.valueOf(10));
        when(materialRepository.findAllByActivoTrueOrderByNombreAsc()).thenReturn(List.of(m1));
        when(alertaRepository.findAllByEstadoOrderByFechaGeneradaDesc(AlertaEstado.activa)).thenReturn(List.of());
        when(exporter.pdf(any(TablaReporte.class))).thenReturn(new byte[]{1, 2, 3});
        when(reporteRepository.save(any(ReporteGenerado.class))).thenAnswer(inv -> {
            ReporteGenerado r = inv.getArgument(0);
            r.setIdReporte(77);
            return r;
        });

        var resultado = service.exportar(new ReporteRequest(TipoReporte.inventario, "pdf", null, null));

        assertThat(resultado.contentType()).isEqualTo("application/pdf");
        assertThat(resultado.idReporte()).isEqualTo(77);
        assertThat(resultado.contenido()).isEqualTo(new byte[]{1, 2, 3});
        // archivo fisico guardado en el directorio configurado
        List<Path> generados;
        try (var stream = Files.list(tempDir)) {
            generados = stream.collect(Collectors.toList());
        }
        assertThat(generados).hasSize(1);
        // el nombre usa el id_reporte (unicidad por diseno, D-21) y no un timestamp
        assertThat(generados.get(0).getFileName().toString()).isEqualTo("reporte-inventario-77.pdf");
        assertThat(resultado.nombreArchivo()).isEqualTo(generados.get(0).getFileName().toString());
        // bitacora (2 saves: insert sin ruta + update con ruta) + auditoria explicita
        verify(reporteRepository, times(2)).save(any(ReporteGenerado.class));
        verify(auditService).registrar(eq("reportes_generados"), eq("77"), eq("CREATE"),
                eq(1), isNull(), anyString());
    }

    @Test
    @DisplayName("exportar: formato invalido -> 400")
    void exportarFormatoInvalido() {
        ReporteRequest req = new ReporteRequest(TipoReporte.inventario, "csv", null, null);

        assertThatThrownBy(() -> service.exportar(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("exportar: sin datos -> 404 (CU-014 alt 2)")
    void exportarSinDatos() {
        when(movimientoRepository.findAllByOrderByFechaMovimientoDesc()).thenReturn(List.of());

        ReporteRequest req = new ReporteRequest(TipoReporte.movimientos, "pdf", null, null);

        assertThatThrownBy(() -> service.exportar(req))
                .isInstanceOf(ApiException.class)
                .extracting("status", InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("bitacora: mapea el historial, mas reciente primero")
    void bitacora() {
        ReporteGenerado registro = new ReporteGenerado();
        registro.setIdReporte(1);
        registro.setIdUsuario(1);
        registro.setTipoReporte("inventario");
        registro.setFormato("pdf");
        registro.setFechaGeneracion(LocalDateTime.of(2026, 9, 5, 10, 0));
        registro.setRutaArchivo("reporte-inventario-20260905-1000.pdf");
        when(reporteRepository.findAllByOrderByFechaGeneracionDesc()).thenReturn(List.of(registro));

        List<ReporteGeneradoResponse> filas = service.bitacora();

        assertThat(filas).hasSize(1);
        ReporteGeneradoResponse r = filas.get(0);
        assertThat(r.idReporte()).isEqualTo(1);
        assertThat(r.tipoReporte()).isEqualTo("inventario");
        assertThat(r.formato()).isEqualTo("pdf");
        assertThat(r.nombreArchivo()).contains("reporte-inventario");
    }

    @Test
    @DisplayName("descargar: devuelve el archivo guardado con su content type")
    void descargar() throws Exception {
        byte[] contenido = {9, 8, 7};
        Path archivo = tempDir.resolve("reporte-inventario-1.pdf");
        Files.write(archivo, contenido);
        ReporteGenerado registro = new ReporteGenerado();
        registro.setIdReporte(1);
        registro.setFormato("pdf");
        registro.setRutaArchivo("reporte-inventario-1.pdf");
        when(reporteRepository.findById(1)).thenReturn(Optional.of(registro));

        var resultado = service.descargar(1);

        assertThat(resultado.contenido()).isEqualTo(contenido);
        assertThat(resultado.contentType()).isEqualTo("application/pdf");
        assertThat(resultado.nombreArchivo()).isEqualTo("reporte-inventario-1.pdf");
    }

    @Test
    @DisplayName("descargar: reporte inexistente -> 404")
    void descargarInexistente() {
        when(reporteRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.descargar(99))
                .isInstanceOf(ApiException.class)
                .extracting("status", InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(auditService, never()).registrar(anyString(), anyString(), anyString(), isNull(), isNull(), isNull());
    }

    @Test
    @DisplayName("descargar: ruta fuera del directorio -> 404 (path traversal)")
    void descargarBloqueaPathTraversal() {
        ReporteGenerado registro = new ReporteGenerado();
        registro.setIdReporte(2);
        registro.setRutaArchivo("../fuera.pdf");
        when(reporteRepository.findById(2)).thenReturn(Optional.of(registro));

        assertThatThrownBy(() -> service.descargar(2))
                .isInstanceOf(ApiException.class)
                .extracting("status", InstanceOfAssertFactories.type(HttpStatus.class))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}