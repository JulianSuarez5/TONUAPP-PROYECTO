package com.tonuapp.ubicaciones;

import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.Lote;
import com.tonuapp.domain.Material;
import com.tonuapp.repository.LoteRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.shared.ApiException;
import com.tonuapp.shared.PagedResponse;
import com.tonuapp.ubicaciones.dto.LoteRequest;
import com.tonuapp.ubicaciones.dto.LoteResponse;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Control de lotes de acopio (RF-015, US-17). Reglas:
 *  - codigo de lote unico;
 *  - la cantidad es SOLO informativa (D-02): el stock real se deriva de
 *    movimientos_inventario, por lo que crear/editar un lote no altera la existencia;
 *  - el material del lote es inmutable tras la creacion (cambiar el material de un lote
 *    corromperia el historial de movimientos que lo referencian);
 *  - soft delete (D-04): no se desactiva un lote referenciado por movimientos.
 */
@Service
public class LoteService {

    /** Tamano maximo de pagina que acepta el listado (evita consultas sin tope). */
    public static final int MAX_PAGE_SIZE = 100;

    private final LoteRepository loteRepository;
    private final MaterialRepository materialRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    public LoteService(LoteRepository loteRepository,
                       MaterialRepository materialRepository,
                       MovimientoInventarioRepository movimientoRepository) {
        this.loteRepository = loteRepository;
        this.materialRepository = materialRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // Lista paginada de lotes activos (RF-015). El filtro opcional q busca por codigo;
    // materialId restringe los lotes de un material concreto
    public PagedResponse<LoteResponse> listarActivos(Integer idMaterial, String q, int page, int size) {
        Pageable pageable = validarPaginacion(page, size);
        Page<Lote> pagina = loteRepository.findAll(filtros(idMaterial, q), pageable);
        return PagedResponse.of(pagina.map(LoteMapper::toResponse));
    }

    // Predicados del listado: activos siempre; q filtra por codigo (contiene), materialId
    // por el material asociado
    private Specification<Lote> filtros(Integer idMaterial, String q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isTrue(root.get("activo")));
            if (StringUtils.hasText(q)) {
                preds.add(cb.like(cb.lower(root.get("codigoLote")), "%" + q.trim().toLowerCase() + "%"));
            }
            if (idMaterial != null) {
                preds.add(cb.equal(root.get("material").get("idMaterial"), idMaterial));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    // Detalle de un lote activo por id, o 404
    public LoteResponse obtener(Integer id) {
        return LoteMapper.toResponse(obtenerActivo(id));
    }

    // Crea un lote para un material activo (US-17). Codigo unico; la cantidad es solo
    // informativa (D-02) y no puede ser negativa
    @Auditable(entidad = "lotes", operacion = "CREATE")
    @Transactional
    public LoteResponse crear(LoteRequest request) {
        Material material = materialRepository.findByIdMaterialAndActivoTrue(request.idMaterial())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
        String codigo = request.codigoLote().trim();
        if (loteRepository.existsByCodigoLote(codigo)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un lote con ese codigo.");
        }

        Lote l = new Lote();
        l.setMaterial(material);
        l.setCodigoLote(codigo);
        l.setCantidad(request.cantidad());
        l.setFechaIngreso(LocalDateTime.now());
        l.setActivo(true);
        return LoteMapper.toResponse(loteRepository.save(l));
    }

    // Actualiza codigo/cantidad de un lote activo. El material NO se puede cambiar: un
    // lote queda ligado al material con el que fue creado para conservar la trazabilidad
    // de los movimientos que lo referencian
    @Auditable(entidad = "lotes", operacion = "UPDATE")
    @Transactional
    public LoteResponse actualizar(Integer id, LoteRequest request) {
        Lote l = obtenerActivo(id);
        String codigo = request.codigoLote().trim();
        if (loteRepository.existsByCodigoLoteAndIdLoteNot(codigo, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un lote con ese codigo.");
        }
        if (!l.getMaterial().getIdMaterial().equals(request.idMaterial())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El material de un lote no se puede cambiar una vez creado (referenciado por movimientos).");
        }

        l.setCodigoLote(codigo);
        l.setCantidad(request.cantidad());
        return LoteMapper.toResponse(loteRepository.save(l));
    }

    // Soft delete (D-04). Bloqueado si el lote esta referenciado por movimientos
    @Auditable(entidad = "lotes", operacion = "DELETE")
    @Transactional
    public void desactivar(Integer id) {
        Lote l = obtenerActivo(id);
        if (movimientoRepository.countByIdLote(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede eliminar un lote referenciado por movimientos.");
        }
        l.setActivo(false);
        loteRepository.save(l);
    }

    // Lotes activos de un material, mas recientes primero (para el selector de lotes en
    // la pantalla de movimientos y en la vista de materiales, RF-015/US-17)
    public List<LoteResponse> listarLotesDeMaterial(Integer idMaterial) {
        materialRepository.findByIdMaterialAndActivoTrue(idMaterial)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
        return loteRepository.findAllByActivoTrueAndMaterial_IdMaterialOrderByFechaIngresoDesc(idMaterial).stream()
                .map(LoteMapper::toResponse)
                .toList();
    }

    // Helper: carga un lote activo o lanza 404
    private Lote obtenerActivo(Integer id) {
        return loteRepository.findByIdLoteAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Lote no encontrado."));
    }

    // Paginacion basica: page >= 0 y size entre 1 y MAX_PAGE_SIZE. Orden por defecto
    // fecha de ingreso desc (mas reciente primero, como el historico de movimientos)
    private Pageable validarPaginacion(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El parametro 'page' no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El parametro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("fechaIngreso")));
    }
}