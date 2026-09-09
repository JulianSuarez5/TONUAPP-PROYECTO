package com.tonuapp.materiales;

import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.Categoria;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.UnidadMedida;
import com.tonuapp.materiales.dto.MaterialRequest;
import com.tonuapp.materiales.dto.MaterialResponse;
import com.tonuapp.repository.CategoriaRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.UnidadMedidaRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD y busqueda de materiales (RF-001 a RF-004).
 * Reglas:
 *  - no se permiten duplicados (mismo nombre + categoria, RF-002);
 *  - la busqueda devuelve solo materiales activos y exige al menos un filtro (RF-003),
 *    con maximo dos filtros simultaneos (RF-001);
 *  - no se elimina un material con movimientos registrados (RF-004);
 *  - soft delete: "eliminar" = activo = false (D-04).
 */
@Service
public class MaterialService {

    /** Tamano maximo de pagina que acepta el listado (evita consultas sin tope). */
    public static final int MAX_PAGE_SIZE = 100;

    private final MaterialRepository materialRepository;
    private final CategoriaRepository categoriaRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    public MaterialService(MaterialRepository materialRepository,
                           CategoriaRepository categoriaRepository,
                           UnidadMedidaRepository unidadMedidaRepository,
                           MovimientoInventarioRepository movimientoRepository) {
        this.materialRepository = materialRepository;
        this.categoriaRepository = categoriaRepository;
        this.unidadMedidaRepository = unidadMedidaRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // Lista paginada de materiales activos, ordenados por nombre (RF-001)
    public PagedResponse<MaterialResponse> listarActivos(int page, int size) {
        Page<Material> result = materialRepository.findAllByActivoTrue(
                validarPaginacion(page, size, "nombre"));
        return PagedResponse.of(result.map(MaterialMapper::toResponse));
    }

    // Busca materiales activos por nombre (contiene) y/o categoria; exige al menos un
    // filtro. Paginado igual que listarActivos (orden por nombre en el Pageable)
    public PagedResponse<MaterialResponse> buscar(String nombre, Integer idCategoria,
                                                  int page, int size) {
        if (!StringUtils.hasText(nombre) && idCategoria == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La busqueda requiere al menos un filtro (nombre o categoria).");
        }
        int filtros = 0;
        if (StringUtils.hasText(nombre)) {
            filtros++;
        }
        if (idCategoria != null) {
            filtros++;
        }
        if (filtros > 2) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Maximo dos filtros de busqueda simultaneos (RF-001).");
        }

        Specification<Material> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isTrue(root.get("activo")));
            if (StringUtils.hasText(nombre)) {
                preds.add(cb.like(cb.lower(root.get("nombre")), "%" + nombre.trim().toLowerCase() + "%"));
            }
            if (idCategoria != null) {
                preds.add(cb.equal(root.get("categoria").get("idCategoria"), idCategoria));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };

        Page<Material> result = materialRepository.findAll(spec, validarPaginacion(page, size, "nombre"));
        return PagedResponse.of(result.map(MaterialMapper::toResponse));
    }

    // Devuelve un material activo por id, o 404 si no existe/inactivo
    public MaterialResponse obtener(Integer id) {
        return MaterialMapper.toResponse(obtenerActivo(id));
    }

    // Crea un material nuevo si no existe otro con el mismo nombre en la categoria;
    // valida categoria/unidad y materializa el stock inicial (D-17)
    // TODO: la Fase 6 (movimientos de inventario) usara esta stock como base y la mantendra
    // actualizada de forma atomica con cada entrada/salida/ajuste
    @Auditable(entidad = "materiales", operacion = "CREATE")
    @Transactional
    public MaterialResponse crear(MaterialRequest request) {
        String nombre = request.nombre().trim();
        if (materialRepository.existsByNombreAndCategoria_IdCategoria(nombre, request.idCategoria())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Ya existe un material con ese nombre en la misma categoria.");
        }
        Categoria categoria = findCategoriaActiva(request.idCategoria());
        UnidadMedida unidad = findUnidad(request.idUnidad());

        Material m = new Material();
        m.setNombre(nombre);
        m.setCategoria(categoria);
        m.setUnidad(unidad);
        m.setStock(request.stock() != null ? request.stock() : BigDecimal.ZERO);
        m.setStockMinimo(request.stockMinimo() != null ? request.stockMinimo() : BigDecimal.ZERO);
        m.setActivo(true);
        m.setFechaRegistro(LocalDateTime.now());
        return MaterialMapper.toResponse(materialRepository.save(m));
    }

    // Actualiza nombre/categoria/unidad/stock de un material activo; reusa la validacion
    // de duplicados excluyendo el propio material
    @Auditable(entidad = "materiales", operacion = "UPDATE")
    @Transactional
    public MaterialResponse actualizar(Integer id, MaterialRequest request) {
        Material m = obtenerActivo(id);
        String nombre = request.nombre().trim();
        if (materialRepository.existsByNombreAndCategoria_IdCategoria(nombre, request.idCategoria())
                && !m.getNombre().equalsIgnoreCase(nombre)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Ya existe un material con ese nombre en la misma categoria.");
        }
        Categoria categoria = findCategoriaActiva(request.idCategoria());
        UnidadMedida unidad = findUnidad(request.idUnidad());

        m.setNombre(nombre);
        m.setCategoria(categoria);
        m.setUnidad(unidad);
        m.setStock(request.stock() != null ? request.stock() : m.getStock());
        m.setStockMinimo(request.stockMinimo() != null ? request.stockMinimo() : m.getStockMinimo());
        m.setFechaActualizacion(LocalDateTime.now());
        return MaterialMapper.toResponse(materialRepository.save(m));
    }

    // Soft delete (D-04): "elimina" = activo=false. Bloqueado si el material tiene
    // movimientos (RF-004)
    @Auditable(entidad = "materiales", operacion = "DELETE")
    @Transactional
    public void desactivar(Integer id) {
        Material m = obtenerActivo(id);
        if (movimientoRepository.countByMaterial_IdMaterial(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede eliminar un material con movimientos registrados (RF-004).");
        }
        m.setActivo(false);
        m.setFechaActualizacion(LocalDateTime.now());
        materialRepository.save(m);
    }

    // Helper: carga un material activo o lanza 404
    private Material obtenerActivo(Integer id) {
        return materialRepository.findByIdMaterialAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
    }

    // Helper: carga una categoria activa o lanza 404
    private Categoria findCategoriaActiva(Integer idCategoria) {
        return categoriaRepository.findById(idCategoria)
                .filter(Categoria::isActivo)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Categoria no encontrada."));
    }

    // Helper: carga una unidad de medida o lanza 404
    private UnidadMedida findUnidad(Integer idUnidad) {
        return unidadMedidaRepository.findById(idUnidad)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Unidad de medida no encontrada."));
    }

    // Paginacion basica: page >= 0 y size entre 1 y MAX_PAGE_SIZE. Orden por defecto
    // por el atributo indicado (para materiales: nombre asc)
    private Pageable validarPaginacion(int page, int size, String ordenarPor) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El parametro 'page' no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El parametro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by(ordenarPor));
    }
}