package com.tonuapp.movimientos;

import com.tonuapp.alertas.AlertaService;
import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.AjusteInventario;
import com.tonuapp.domain.EstadoMovimiento;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MovimientoInventario;
import com.tonuapp.domain.TipoMovimiento;
import com.tonuapp.domain.Usuario;
import com.tonuapp.movimientos.dto.AjusteRequest;
import com.tonuapp.movimientos.dto.AjusteResponse;
import com.tonuapp.movimientos.dto.MovimientoRequest;
import com.tonuapp.movimientos.dto.MovimientoResponse;
import com.tonuapp.repository.AjusteInventarioRepository;
import com.tonuapp.repository.LoteRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.UsuarioRepository;
import com.tonuapp.repository.ZonaAcopioRepository;
import com.tonuapp.security.AuthenticatedUser;
import com.tonuapp.security.SecurityUtils;
import com.tonuapp.shared.ApiException;
import com.tonuapp.shared.PagedResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Movimientos de inventario (RF-004, RF-011, RF-013). Reglas:
 *  - cantidad siempre > 0; el efecto sobre el stock lo da el tipo (entrada +,
 *    salida_venta/salida_merma -) o, para ajuste, el stock objetivo (D-18).
 *  - ningun movimiento deja el stock en negativo (RF-011/RF-013, AGENTS §18).
 *  - el stock de Material se actualiza en la MISMA transaccion que el movimiento
 *    (D-03); Material tiene @Version para evitar condiciones de carrera.
 *  - los movimientos se anulan (estado = anulado), nunca se borran (D-04).
 */
@Service
public class MovimientoService {

    /** Tamano maximo de pagina que acepta el listado (evita consultas sin tope). */
    public static final int MAX_PAGE_SIZE = 100;

    private final MovimientoInventarioRepository movimientoRepository;
    private final MaterialRepository materialRepository;
    private final UsuarioRepository usuarioRepository;
    private final AjusteInventarioRepository ajusteRepository;
    private final LoteRepository loteRepository;
    private final ZonaAcopioRepository zonaRepository;
    private final AlertaService alertaService;

    public MovimientoService(MovimientoInventarioRepository movimientoRepository,
                             MaterialRepository materialRepository,
                             UsuarioRepository usuarioRepository,
                             AjusteInventarioRepository ajusteRepository,
                             LoteRepository loteRepository,
                             ZonaAcopioRepository zonaRepository,
                             AlertaService alertaService) {
        this.movimientoRepository = movimientoRepository;
        this.materialRepository = materialRepository;
        this.usuarioRepository = usuarioRepository;
        this.ajusteRepository = ajusteRepository;
        this.loteRepository = loteRepository;
        this.zonaRepository = zonaRepository;
        this.alertaService = alertaService;
    }

    // Historico paginado (mas reciente primero) con filtros opcionales por material,
    // tipo, estado y rango de fechas. El orden siempre es fechaMovimiento desc (D-04:
    // los movimientos se anulan, no se borran, por eso el listado debe poder filtrarse)
    public PagedResponse<MovimientoResponse> listar(Integer idMaterial, TipoMovimiento tipo,
                                                    EstadoMovimiento estado, LocalDate desde,
                                                    LocalDate hasta, int page, int size) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La fecha 'desde' no puede ser posterior a 'hasta'.");
        }
        Pageable pageable = validarPaginacion(page, size);
        LocalDateTime desdeDt = desde != null ? desde.atStartOfDay() : null;
        LocalDateTime hastaDt = hasta != null ? hasta.atTime(LocalTime.MAX) : null;
        Page<MovimientoInventario> result = movimientoRepository.findAll(
                filtrosMovimiento(idMaterial, tipo, estado, desdeDt, hastaDt), pageable);
        return PagedResponse.of(result.map(MovimientoMapper::toResponse));
    }

    // Predicados combinados del listado; cada filtro nulo se ignora. El orden va en el
    // Pageable (fechaMovimiento desc) para no mezclar orden dentro de la Specification
    private Specification<MovimientoInventario> filtrosMovimiento(Integer idMaterial,
                                                                  TipoMovimiento tipo,
                                                                  EstadoMovimiento estado,
                                                                  LocalDateTime desde,
                                                                  LocalDateTime hasta) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (idMaterial != null) {
                preds.add(cb.equal(root.get("material").get("idMaterial"), idMaterial));
            }
            if (tipo != null) {
                preds.add(cb.equal(root.get("tipoMovimiento"), tipo));
            }
            if (estado != null) {
                preds.add(cb.equal(root.get("estado"), estado));
            }
            if (desde != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("fechaMovimiento"), desde));
            }
            if (hasta != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("fechaMovimiento"), hasta));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    // Registra entrada o salida y actualiza el stock en la misma transaccion, sin
    // permitir que quede negativo. El ajuste no entra por aqui (va por /ajuste)
    @Auditable(entidad = "movimientos_inventario", operacion = "MOVEMENT")
    @Transactional
    public MovimientoResponse registrarMovimiento(MovimientoRequest request) {
        if (request.tipo() == TipoMovimiento.ajuste) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Los ajustes se registran con stock objetivo en /movimientos/ajuste (RF-011).");
        }
        if (request.cantidad() == null || request.cantidad().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La cantidad debe ser mayor que 0.");
        }
        Material m = cargarMaterialActivo(request.idMaterial());
        // RF-015: si el movimiento referencia lote y/o zona, deben existir y estar activos
        // (antes caia en 500 por la FK de la BD; ahora devuelve un error limpio)
        validarLoteYZona(request.idLote(), request.idZona());
        BigDecimal efecto = request.cantidad().multiply(signoDe(request.tipo()));
        BigDecimal nuevoStock = m.getStock().add(efecto);
        validarStockNoNegativo(nuevoStock, request.tipo());

        MovimientoInventario mov = new MovimientoInventario();
        mov.setMaterial(m);
        mov.setUsuario(usuarioActual());
        mov.setTipoMovimiento(request.tipo());
        mov.setCantidad(request.cantidad());
        mov.setMotivo(trimable(request.motivo()));
        mov.setObservaciones(trimable(request.observaciones()));
        mov.setIdLote(request.idLote());
        mov.setIdZona(request.idZona());
        mov.setEstado(EstadoMovimiento.activo);
        mov.setFechaMovimiento(LocalDateTime.now());

        m.setStock(nuevoStock);
        m.setFechaActualizacion(LocalDateTime.now());
        MovimientoInventario guardado = movimientoRepository.save(mov);
        // RF-012: si el material quedo bajo el minimo, dispara la alerta (misma tx)
        alertaService.generarSiCorresponde(m);
        return MovimientoMapper.toResponse(guardado);
    }

    // Ajuste a stock objetivo (RF-011): el stock queda en la cifra contada. La cantidad
    // del movimiento guarda |diferencia| (siempre positiva, D-02); el signo del efecto
    // se deriva comparando con el stock vigente. Ademas guarda la conciliacion en
    // ajustes_inventario (anterior/nueva) para poder anularla despues (D-19)
    @Auditable(entidad = "movimientos_inventario", operacion = "ADJUST")
    @Transactional
    public MovimientoResponse registrarAjuste(AjusteRequest request) {
        Material m = cargarMaterialActivo(request.idMaterial());
        BigDecimal objetivo = request.cantidadNueva();
        BigDecimal anterior = m.getStock();
        BigDecimal diferencia = objetivo.subtract(anterior);
        if (diferencia.signum() == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El stock ya coincide con la cifra contada; no hay nada que ajustar.");
        }

        MovimientoInventario mov = new MovimientoInventario();
        mov.setMaterial(m);
        mov.setUsuario(usuarioActual());
        mov.setTipoMovimiento(TipoMovimiento.ajuste);
        mov.setCantidad(diferencia.abs());
        mov.setMotivo(request.motivo().trim());
        mov.setEstado(EstadoMovimiento.activo);
        mov.setFechaMovimiento(LocalDateTime.now());

        AjusteInventario ajuste = new AjusteInventario();
        ajuste.setMaterial(m);
        ajuste.setUsuario(mov.getUsuario());
        ajuste.setCantidadAnterior(anterior);
        ajuste.setCantidadNueva(objetivo);
        ajuste.setMotivo(request.motivo().trim());
        ajuste.setFechaAjuste(LocalDateTime.now());

        m.setStock(objetivo);
        m.setFechaActualizacion(LocalDateTime.now());
        // la FK a id_movimiento solo queda al guardar el movimiento (recibe su id)
        ajuste.setMovimiento(movimientoRepository.save(mov));
        ajusteRepository.save(ajuste);
        // RF-012: un ajuste a la baja tambien puede dejar bajo el minimo
        alertaService.generarSiCorresponde(m);
        return MovimientoMapper.toResponse(mov);
    }

    // Anula un movimiento (D-04/US-22): revierte su efecto sobre el stock y lo marca
    // anulado. Un ajuste se revierte al valor contado ANTES (cantidad_anterior) usando
    // ajustes_inventario (D-19); si no existe la fila no se puede revertir con precision
    @Auditable(entidad = "movimientos_inventario", operacion = "UPDATE")
    @Transactional
    public MovimientoResponse anular(Integer idMovimiento) {
        MovimientoInventario mov = cargarMovimiento(idMovimiento);
        if (mov.getEstado() == EstadoMovimiento.anulado) {
            throw new ApiException(HttpStatus.CONFLICT, "El movimiento ya esta anulado.");
        }

        Material m = mov.getMaterial();
        BigDecimal nuevoStock;
        if (mov.getTipoMovimiento() == TipoMovimiento.ajuste) {
            AjusteInventario ajuste = ajusteRepository.findByMovimiento_IdMovimiento(idMovimiento)
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                            "No hay detalle de ajuste (ajustes_inventario) para revertir este movimiento."));
            nuevoStock = ajuste.getCantidadAnterior();
        } else {
            BigDecimal efectoOriginal = signoDe(mov.getTipoMovimiento()).multiply(mov.getCantidad());
            nuevoStock = m.getStock().subtract(efectoOriginal);
        }
        validarStockNoNegativo(nuevoStock, mov.getTipoMovimiento());

        mov.setEstado(EstadoMovimiento.anulado);
        m.setStock(nuevoStock);
        m.setFechaActualizacion(LocalDateTime.now());
        MovimientoInventario guardado = movimientoRepository.save(mov);
        // RF-012: una anulacion (p.ej. de una entrada) tambien puede dejar bajo el minimo
        alertaService.generarSiCorresponde(m);
        return MovimientoMapper.toResponse(guardado);
    }

    // Historico de conciliaciones (RF-011): anterior/nueva por ajuste, mas reciente primero
    public List<AjusteResponse> listarAjustes() {
        return ajusteRepository.findAllByOrderByFechaAjusteDesc()
                .stream()
                .map(AjusteMapper::toResponse)
                .toList();
    }

    // Signo del efecto de cada tipo: entrada suma, salidas restan
    private BigDecimal signoDe(TipoMovimiento tipo) {
        return switch (tipo) {
            case entrada -> BigDecimal.ONE;
            case salida_venta, salida_merma -> BigDecimal.ONE.negate();
            case ajuste -> throw new IllegalStateException("El ajuste usa stock objetivo, no signo fijo.");
        };
    }

    // Regla transversal (AGENTS 18): jamas dejar el stock en negativo
    private void validarStockNoNegativo(BigDecimal nuevoStock, TipoMovimiento tipo) {
        if (nuevoStock.signum() < 0) {
            String mensaje = switch (tipo) {
                case entrada -> "Stock no puede quedar en negativo.";
                case ajuste -> "La reversa del ajuste dejaria el stock en negativo.";
                case salida_venta, salida_merma -> "Stock insuficiente: la salida supera la existencia disponible.";
            };
            throw new ApiException(HttpStatus.CONFLICT, mensaje);
        }
    }

    // Helper: material activo o 404
    private Material cargarMaterialActivo(Integer idMaterial) {
        return materialRepository.findByIdMaterialAndActivoTrue(idMaterial)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
    }

    // Helper: valida que el lote y la zona referenciados por un movimiento existan y esten
    // activos (RF-015). Los indices son opcionales en el movimiento (pueden ser null)
    private void validarLoteYZona(Integer idLote, Integer idZona) {
        if (idLote != null && loteRepository.findByIdLoteAndActivoTrue(idLote).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Lote no encontrado.");
        }
        if (idZona != null && zonaRepository.findByIdZonaAndActivoTrue(idZona).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Zona no encontrada.");
        }
    }

    // Helper: movimiento existente o 404
    private MovimientoInventario cargarMovimiento(Integer idMovimiento) {
        return movimientoRepository.findById(idMovimiento)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Movimiento no encontrado."));
    }

    // Helper: usuario autenticado como entidad (referencia, sin query extra)
    private Usuario usuarioActual() {
        Integer idUsuario = SecurityUtils.currentUser()
                .map(AuthenticatedUser::idUsuario)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Debe autenticarse."));
        return usuarioRepository.getReferenceById(idUsuario);
    }

    // Helper: limpia textos opcionales ("" o espacios -> null)
    private String trimable(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    // Paginacion basica: page >= 0 y size entre 1 y MAX_PAGE_SIZE. Orden por defecto
    // fechaMovimiento desc (mas reciente primero) para el historico
    private Pageable validarPaginacion(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El parametro 'page' no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El parametro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("fechaMovimiento")));
    }
}