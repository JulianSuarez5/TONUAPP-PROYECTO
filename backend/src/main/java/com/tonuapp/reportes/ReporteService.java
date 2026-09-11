package com.tonuapp.reportes;

import com.tonuapp.audit.AuditService;
import com.tonuapp.config.ReporteProperties;
import com.tonuapp.domain.AjusteInventario;
import com.tonuapp.domain.AlertaEstado;
import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.domain.ReporteGenerado;
import com.tonuapp.domain.TipoMovimiento;
import com.tonuapp.movimientos.MovimientoMapper;
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
import com.tonuapp.security.SecurityUtils;
import com.tonuapp.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reportes de inventario (RF-014, D-21). Reglas:
 *  - fuente de datos: SOLO tablas de negocio (materiales, movimientos_inventario,
 *    ajustes_inventario, alertas_inventario). audit_log NO es fuente veraz (D-06:
 *    historial de INTENTOS, no de operaciones persistidas).
 *  - consultas JSON para el frontend + exportacion pdf/xlsx con bitacora en
 *    reportes_generados (quien/cuando/tipo/formato/rango) y auditoria explicita.
 *  - rango de fechas: ambas o ninguna; desde <= hasta; si va solo una -> 400.
 *  - el resumen usa SOLO movimientos activos (los anulados fueron revertidos) y el
 *    delta de cada ajuste (cantidadNueva-anterior, D-19) para que el efecto neto cierre.
 */
@Service
public class ReporteService {

    private static final String FORMATO_PDF = "pdf";
    private static final String FORMATO_XLSX = "xlsx";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final MaterialRepository materialRepository;
    private final MovimientoInventarioRepository movimientoRepository;
    private final AjusteInventarioRepository ajusteRepository;
    private final AlertaInventarioRepository alertaRepository;
    private final ReporteGeneradoRepository reporteRepository;
    private final ReporteExporter exporter;
    private final AuditService auditService;
    private final ReporteProperties properties;

    public ReporteService(MaterialRepository materialRepository,
                          MovimientoInventarioRepository movimientoRepository,
                          AjusteInventarioRepository ajusteRepository,
                          AlertaInventarioRepository alertaRepository,
                          ReporteGeneradoRepository reporteRepository,
                          ReporteExporter exporter,
                          AuditService auditService,
                          ReporteProperties properties) {
        this.materialRepository = materialRepository;
        this.movimientoRepository = movimientoRepository;
        this.ajusteRepository = ajusteRepository;
        this.alertaRepository = alertaRepository;
        this.reporteRepository = reporteRepository;
        this.exporter = exporter;
        this.auditService = auditService;
        this.properties = properties;
    }

    // Resultado de una exportacion: binario + metadatos para armar la respuesta HTTP
    public record ResultadoExportacion(byte[] contenido, String nombreArchivo, String contentType, int idReporte) {
    }

    // Resultado de una descarga desde la bitacora
    public record DescargaResultado(byte[] contenido, String nombreArchivo, String contentType) {
    }

    // --- Consultas (para el frontend y como base de la exportacion) ---

    public List<ReporteInventarioRow> listarInventario() {
        Set<Integer> activas = alertasActivasPorMaterial();
        return materialRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(m -> filaInventario(m, activas))
                .toList();
    }

    public List<ReporteInventarioRow> bajoStock() {
        Set<Integer> activas = alertasActivasPorMaterial();
        return materialRepository.findAllActivosBajoMinimo().stream()
                .map(m -> filaInventario(m, activas))
                .toList();
    }

    public List<MovimientoResponse> listarMovimientos(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        return movimientosEnRango(desde, hasta).stream()
                .map(MovimientoMapper::toResponse)
                .toList();
    }

    public List<ReporteResumenRow> resumen(LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);
        Map<Integer, Acumulador> porMaterial = new HashMap<>();
        for (MovimientoInventario mov : movimientosEnRango(desde, hasta)) {
            if (mov.getEstado() != EstadoMovimiento.activo) {
                continue;
            }
            Acumulador acc = porMaterial.computeIfAbsent(mov.getMaterial().getIdMaterial(), id ->
                    new Acumulador(mov.getMaterial().getIdMaterial(), mov.getMaterial().getNombre(),
                            mov.getMaterial().getStock()));
            switch (mov.getTipoMovimiento()) {
                case entrada -> acc.entradas = acc.entradas.add(mov.getCantidad());
                case salida_venta -> acc.salidasVenta = acc.salidasVenta.add(mov.getCantidad());
                case salida_merma -> acc.salidasMerma = acc.salidasMerma.add(mov.getCantidad());
                case ajuste -> { /* el delta del ajuste se suma desde ajustes_inventario */ }
            }
        }
        for (AjusteInventario ajuste : ajustesEnRango(desde, hasta)) {
            // Mismo criterio que los movimientos: un ajuste cuya movimiento fue anulado
            // (D-04/D-19) NO suma su delta, y si la fila no esta ligada a ningun
            // movimiento activo tampoco (no se puede confirmar que sea una operacion real)
            MovimientoInventario movAjuste = ajuste.getMovimiento();
            if (movAjuste == null || movAjuste.getEstado() != EstadoMovimiento.activo) {
                continue;
            }
            Acumulador acc = porMaterial.computeIfAbsent(ajuste.getMaterial().getIdMaterial(), id ->
                    new Acumulador(ajuste.getMaterial().getIdMaterial(), ajuste.getMaterial().getNombre(),
                            ajuste.getMaterial().getStock()));
            acc.ajusteNeto = acc.ajusteNeto.add(ajuste.getCantidadNueva().subtract(ajuste.getCantidadAnterior()));
        }
        return porMaterial.values().stream()
                .map(Acumulador::aFila)
                .sorted(java.util.Comparator.comparing(ReporteResumenRow::nombreMaterial))
                .toList();
    }

    // --- Exportacion (RF-014) ---

    @Transactional
    public ResultadoExportacion exportar(ReporteRequest request) {
        validarRango(request.desde(), request.hasta());
        if (request.formato() == null
                || !(request.formato().equalsIgnoreCase(FORMATO_PDF) || request.formato().equalsIgnoreCase(FORMATO_XLSX))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El formato debe ser 'pdf' o 'xlsx'.");
        }

        TablaReporte tabla = construirTabla(request.tipo(), request.desde(), request.hasta());
        if (tabla.filas().isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "No hay datos para el reporte en el periodo seleccionado.");
        }

        String formato = request.formato().toLowerCase();
        byte[] contenido = FORMATO_PDF.equals(formato)
                ? exporter.pdf(tabla)
                : exporter.xlsx(tabla);
        // Unicidad por diseno: primero se guarda la fila (obtiene el id_reporte
        // autogenerado) y ESE id forma parte del nombre del archivo. Asi dos
        // exportaciones del mismo tipo nunca colisionan (aunque ocurran en el
        // mismo segundo), y la bitacora apunta a un archivo propio.
        ReporteGenerado registro = registrarBitacora(request);
        String nombreArchivo = "reporte-" + request.tipo().name() + "-"
                + registro.getIdReporte() + "." + formato;
        guardarArchivo(nombreArchivo, contenido);
        registro.setRutaArchivo(nombreArchivo);
        reporteRepository.save(registro);

        // Auditoria explicita (id_registro = id_reporte) como en alertas (D-20): el
        // AOP no sirve aqui porque el id nace en esta transaccion
        Integer idUsuario = idUsuarioActual();
        String despues = "{\"id_reporte\":%d,\"tipo\":\"%s\",\"formato\":\"%s\",\"archivo\":\"%s\"}"
                .formatted(registro.getIdReporte(), request.tipo().name(), formato, nombreArchivo);
        auditService.registrar("reportes_generados", String.valueOf(registro.getIdReporte()),
                "CREATE", idUsuario, null, despues);

        String contentType = FORMATO_PDF.equals(formato)
                ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return new ResultadoExportacion(contenido, nombreArchivo, contentType, registro.getIdReporte());
    }

    public List<ReporteGeneradoResponse> bitacora() {
        return reporteRepository.findAllByOrderByFechaGeneracionDesc()
                .stream()
                .map(r -> new ReporteGeneradoResponse(
                        r.getIdReporte(),
                        r.getIdUsuario(),
                        r.getTipoReporte(),
                        r.getFormato(),
                        r.getFechaGeneracion(),
                        r.getRangoFechaInicio(),
                        r.getRangoFechaFin(),
                        r.getRutaArchivo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DescargaResultado descargar(Integer idReporte) {
        ReporteGenerado registro = reporteRepository.findById(idReporte)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reporte no encontrado."));
        if (registro.getRutaArchivo() == null || registro.getRutaArchivo().isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El reporte no tiene archivo asociado.");
        }
        Path base = Path.of(properties.getDirectorio()).toAbsolutePath().normalize();
        Path archivo = base.resolve(registro.getRutaArchivo()).normalize();
        // Prevencion de path traversal: el archivo debe quedar dentro del directorio
        if (!archivo.startsWith(base) || !Files.exists(archivo)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "El archivo del reporte no esta disponible.");
        }
        try {
            String contentType = FORMATO_PDF.equals(registro.getFormato())
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            return new DescargaResultado(Files.readAllBytes(archivo), registro.getRutaArchivo(), contentType);
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el archivo del reporte.");
        }
    }

    // --- Construccion interna ---

    private TablaReporte construirTabla(TipoReporte tipo, LocalDate desde, LocalDate hasta) {
        return switch (tipo) {
            case inventario -> tablaInventario();
            case bajo_stock -> tablaBajoStock();
            case movimientos -> tablaMovimientos(desde, hasta);
            case resumen -> tablaResumen(desde, hasta);
        };
    }

    private TablaReporte tablaInventario() {
        List<ReporteInventarioRow> filas = listarInventario();
        return new TablaReporte("Reporte de inventario",
                encabezado("Stock actual de todos los materiales activos"),
                new String[]{"Codigo", "Material", "Categoria", "Unidad", "Stock", "Stock minimo", "Alerta"},
                filas.stream().map(r -> new String[]{
                        String.valueOf(r.idMaterial()), r.nombreMaterial(), r.categoria(), r.unidad(),
                        r.stock().toPlainString(), r.stockMinimo().toPlainString(),
                        r.conAlertaActiva() ? "activa" : "no"}).collect(Collectors.toList()),
                "");
    }

    private TablaReporte tablaBajoStock() {
        List<ReporteInventarioRow> filas = bajoStock();
        return new TablaReporte("Reporte de bajo stock",
                encabezado("Materiales bajo el stock minimo"),
                new String[]{"Codigo", "Material", "Categoria", "Unidad", "Stock", "Stock minimo", "Alerta"},
                filas.stream().map(r -> new String[]{
                        String.valueOf(r.idMaterial()), r.nombreMaterial(), r.categoria(), r.unidad(),
                        r.stock().toPlainString(), r.stockMinimo().toPlainString(),
                        r.conAlertaActiva() ? "activa" : "no"}).collect(Collectors.toList()),
                "");
    }

    private TablaReporte tablaMovimientos(LocalDate desde, LocalDate hasta) {
        List<MovimientoResponse> filas = listarMovimientos(desde, hasta);
        return new TablaReporte("Reporte de movimientos",
                encabezado("Historial de movimientos de inventario"),
                new String[]{"N", "Fecha", "Material", "Tipo", "Cantidad", "Usuario", "Estado", "Motivo"},
                filas.stream().map(r -> new String[]{
                        String.valueOf(r.idMovimiento()),
                        r.fechaMovimiento() == null ? "" : r.fechaMovimiento().format(FECHA),
                        r.nombreMaterial(), r.tipo().name(), r.cantidad().toPlainString(),
                        r.nombreUsuario() == null ? "" : r.nombreUsuario(),
                        r.estado().name(), r.motivo() == null ? "" : r.motivo()}).collect(Collectors.toList()),
                "");
    }

    private TablaReporte tablaResumen(LocalDate desde, LocalDate hasta) {
        List<ReporteResumenRow> filas = resumen(desde, hasta);
        return new TablaReporte("Reporte resumen por material",
                encabezado("Totales de movimientos por material"),
                new String[]{"Codigo", "Material", "Entradas", "Salidas venta", "Salidas merma",
                        "Ajuste neto", "Efecto neto", "Stock actual"},
                filas.stream().map(r -> new String[]{
                        String.valueOf(r.idMaterial()), r.nombreMaterial(),
                        r.entradas().toPlainString(), r.salidasVenta().toPlainString(),
                        r.salidasMerma().toPlainString(), r.ajusteNeto().toPlainString(),
                        r.efectoNeto().toPlainString(), r.stockActual().toPlainString()})
                        .collect(Collectors.toList()),
                "");
    }

    private String encabezado(String detalle) {
        String base = "Agregados el Tonusco S.A.S. (TONUAPP)"; // placeholder del logo (RF-014, D-21)
        return base + " - Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " - " + detalle;
    }

    // Valida el rango de fechas: ambas o ninguna, y desde <= hasta
    private void validarRango(LocalDate desde, LocalDate hasta) {
        if ((desde == null) != (hasta == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar el rango completo (desde y hasta) o ninguno.");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
    }

    // Movimientos de un rango (o todo el historial si no hay rango)
    private List<MovimientoInventario> movimientosEnRango(LocalDate desde, LocalDate hasta) {
        if (desde == null) {
            return movimientoRepository.findAllByOrderByFechaMovimientoDesc();
        }
        return movimientoRepository.findAllByFechaMovimientoBetweenOrderByFechaMovimientoDesc(
                desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
    }

    // Ajustes de un rango (o todos si no hay rango), para el del delta en el resumen
    private List<AjusteInventario> ajustesEnRango(LocalDate desde, LocalDate hasta) {
        if (desde == null) {
            return ajusteRepository.findAllByOrderByFechaAjusteDesc();
        }
        return ajusteRepository.findAllByFechaAjusteBetween(desde.atStartOfDay(), hasta.atTime(LocalTime.MAX));
    }

    // Ids de materiales con una alerta ACTIVA (una por material, D-20)
    private Set<Integer> alertasActivasPorMaterial() {
        return alertaRepository.findAllByEstadoOrderByFechaGeneradaDesc(AlertaEstado.activa)
                .stream()
                .map(a -> a.getMaterial().getIdMaterial())
                .collect(Collectors.toSet());
    }

    private ReporteInventarioRow filaInventario(Material m, Set<Integer> alertasActivas) {
        return new ReporteInventarioRow(
                m.getIdMaterial(),
                m.getNombre(),
                m.getCategoria().getNombre(),
                m.getUnidad().getNombre(),
                m.getStock(),
                m.getStockMinimo(),
                alertasActivas.contains(m.getIdMaterial()));
    }

    private void guardarArchivo(String nombre, byte[] contenido) {
        try {
            Path dir = Path.of(properties.getDirectorio());
            Files.createDirectories(dir);
            Files.write(dir.resolve(nombre), contenido);
        } catch (java.io.IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el reporte generado.");
        }
    }

    private ReporteGenerado registrarBitacora(ReporteRequest request) {
        ReporteGenerado registro = new ReporteGenerado();
        registro.setIdUsuario(idUsuarioActual());
        registro.setTipoReporte(request.tipo().name());
        registro.setFormato(request.formato().toLowerCase());
        registro.setFechaGeneracion(LocalDateTime.now());
        registro.setRangoFechaInicio(request.desde());
        registro.setRangoFechaFin(request.hasta());
        return reporteRepository.save(registro);
    }

    private Integer idUsuarioActual() {
        return SecurityUtils.currentUser()
                .map(AuthenticatedUser::idUsuario)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Debe autenticarse."));
    }

    // Acumulador interno del reporte resumen
    private static final class Acumulador {
        private final Integer idMaterial;
        private BigDecimal entradas = BigDecimal.ZERO;
        private BigDecimal salidasVenta = BigDecimal.ZERO;
        private BigDecimal salidasMerma = BigDecimal.ZERO;
        private BigDecimal ajusteNeto = BigDecimal.ZERO;
        private final String nombre;
        private final BigDecimal stockActual;

        private Acumulador(Integer idMaterial, String nombre, BigDecimal stockActual) {
            this.idMaterial = idMaterial;
            this.nombre = nombre;
            this.stockActual = stockActual;
        }

        private ReporteResumenRow aFila() {
            BigDecimal efectoNeto = entradas.subtract(salidasVenta).subtract(salidasMerma).add(ajusteNeto);
            return new ReporteResumenRow(idMaterial, nombre, entradas, salidasVenta, salidasMerma,
                    ajusteNeto, efectoNeto, stockActual);
        }
    }
}