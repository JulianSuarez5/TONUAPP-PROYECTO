package com.tonuapp.ubicaciones;

import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.ZonaAcopio;
import com.tonuapp.materiales.MaterialMapper;
import com.tonuapp.materiales.dto.MaterialResponse;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.MovimientoInventarioRepository;
import com.tonuapp.repository.ZonaAcopioRepository;
import com.tonuapp.shared.ApiException;
import com.tonuapp.shared.PagedResponse;
import com.tonuapp.ubicaciones.dto.ZonaAcopioRequest;
import com.tonuapp.ubicaciones.dto.ZonaAcopioResponse;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Control de zonas de acopio (RF-015, US-16). Reglas:
 *  - nombre de zona unico (case-insensitive) y capacida maxima no negativa;
 *  - RF-015: una zona con tipo_material_permitido solo admite materiales cuya categoria
 *    coincida; al cambiar el tipo de una zona se verifica que los materiales ya asignados
 *    sigan siendo compatibles;
 *  - el material se asigna a una sola zona (materiales.id_zona) y la asignacion se valida
 *    contra el tipo permitido de la zona;
 *  - soft delete (D-04): no se desactiva una zona con materiales activos asignados ni con
 *    movimientos que la referencien.
 */
@Service
public class ZonaAcopioService {

    /** Tamano maximo de pagina que acepta el listado (evita consultas sin tope). */
    public static final int MAX_PAGE_SIZE = 100;

    private final ZonaAcopioRepository zonaRepository;
    private final MaterialRepository materialRepository;
    private final MovimientoInventarioRepository movimientoRepository;

    public ZonaAcopioService(ZonaAcopioRepository zonaRepository,
                             MaterialRepository materialRepository,
                             MovimientoInventarioRepository movimientoRepository) {
        this.zonaRepository = zonaRepository;
        this.materialRepository = materialRepository;
        this.movimientoRepository = movimientoRepository;
    }

    // Lista paginada de zonas activas (RF-015) con la cantidad de materiales asignados.
    // El filtro q opcional busca por nombre o tipo de material permitido
    public PagedResponse<ZonaAcopioResponse> listarActivas(String q, int page, int size) {
        Pageable pageable = validarPaginacion(page, size);
        Page<ZonaAcopio> pagina = zonaRepository.findAll(filtros(q), pageable);
        Map<Integer, Long> conteo = cantidadMaterialesPorZona(pagina.getContent().stream()
                .map(ZonaAcopio::getIdZona)
                .collect(Collectors.toSet()));
        return PagedResponse.of(pagina.map(z -> ZonaAcopioMapper.toResponse(z,
                conteo.getOrDefault(z.getIdZona(), 0L))));
    }

    // Predicados del listado: activas siempre; con q se filtra por nombre o tipo permitido
    private Specification<ZonaAcopio> filtros(String q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isTrue(root.get("activo")));
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("nombreZona")), like),
                        cb.like(cb.lower(root.get("tipoMaterialPermitido")), like)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    // Conteo de materiales activos agrupado por zona en una sola consulta
    private Map<Integer, Long> cantidadMaterialesPorZona(Collection<Integer> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return materialRepository.findAllByActivoTrueAndZona_IdZonaIn(ids).stream()
                .collect(Collectors.groupingBy(m -> m.getZona().getIdZona(), Collectors.counting()));
    }

    // Detalle de una zona activa por id, o 404
    public ZonaAcopioResponse obtener(Integer id) {
        ZonaAcopio z = obtenerActiva(id);
        return ZonaAcopioMapper.toResponse(z, materialRepository.findAllByActivoTrueAndZona_IdZona(id).size());
    }

    // Crea una zona validando nombre unico (RF-015)
    @Auditable(entidad = "zonas_acopio", operacion = "CREATE")
    @Transactional
    public ZonaAcopioResponse crear(ZonaAcopioRequest request) {
        String nombre = request.nombreZona().trim();
        if (zonaRepository.existsByNombreZonaIgnoreCase(nombre)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una zona con ese nombre.");
        }

        ZonaAcopio z = new ZonaAcopio();
        z.setNombreZona(nombre);
        z.setCapacidadMaxima(request.capacidadMaxima());
        z.setTipoMaterialPermitido(trimable(request.tipoMaterialPermitido()));
        z.setActivo(true);
        return ZonaAcopioMapper.toResponse(zonaRepository.save(z), 0L);
    }

    // Actualiza los datos de una zona; si cambia el tipo permitido, los materiales ya
    // asignados deben seguir siendo compatibles (RF-015)
    @Auditable(entidad = "zonas_acopio", operacion = "UPDATE")
    @Transactional
    public ZonaAcopioResponse actualizar(Integer id, ZonaAcopioRequest request) {
        ZonaAcopio z = obtenerActiva(id);
        String nombre = request.nombreZona().trim();
        if (zonaRepository.existsByNombreZonaIgnoreCaseAndIdZonaNot(nombre, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una zona con ese nombre.");
        }

        String nuevoTipo = trimable(request.tipoMaterialPermitido());
        if (!igualesSinMayusculas(z.getTipoMaterialPermitido(), nuevoTipo)) {
            validarCompatibilidadMateriales(id, nuevoTipo);
        }

        z.setNombreZona(nombre);
        z.setCapacidadMaxima(request.capacidadMaxima());
        z.setTipoMaterialPermitido(nuevoTipo);
        long cantidad = materialRepository.findAllByActivoTrueAndZona_IdZona(id).size();
        return ZonaAcopioMapper.toResponse(zonaRepository.save(z), cantidad);
    }

    // Soft delete (D-04). Bloqueado si la zona tiene materiales activos asignados o
    // movimientos que la referencien (RF-015: no se puede dejar una zona en uso)
    @Auditable(entidad = "zonas_acopio", operacion = "DELETE")
    @Transactional
    public void desactivar(Integer id) {
        ZonaAcopio z = obtenerActiva(id);
        if (!materialRepository.findAllByActivoTrueAndZona_IdZona(id).isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede eliminar una zona con materiales asignados (RF-015).");
        }
        if (movimientoRepository.countByIdZona(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede eliminar una zona referenciada por movimientos.");
        }
        z.setActivo(false);
        zonaRepository.save(z);
    }

    // Materiales activos asignados a la zona (RF-015: indicador de ubicacion en consulta)
    public List<MaterialResponse> listarMaterialesDeZona(Integer idZona) {
        obtenerActiva(idZona);
        return materialRepository.findAllByActivoTrueAndZona_IdZona(idZona).stream()
                .map(MaterialMapper::toResponse)
                .toList();
    }

    // Asigna un material a una zona (RF-015). La zona solo admite materiales de la
    // categoria declarada en tipo_material_permitido (si esta definido)
    @Auditable(entidad = "materiales", operacion = "UPDATE")
    @Transactional
    public MaterialResponse asignarMaterial(Integer idZona, Integer idMaterial) {
        ZonaAcopio zona = obtenerActiva(idZona);
        Material material = obtenerMaterialActivo(idMaterial);
        String tipo = zona.getTipoMaterialPermitido();
        if (StringUtils.hasText(tipo) && !tipo.equalsIgnoreCase(material.getCategoria().getNombre())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La zona solo admite materiales de tipo '" + tipo + "' (RF-015).");
        }
        if (material.getZona() != null && material.getZona().getIdZona().equals(idZona)) {
            throw new ApiException(HttpStatus.CONFLICT, "El material ya esta asignado a esta zona.");
        }
        material.setZona(zona);
        return MaterialMapper.toResponse(materialRepository.save(material));
    }

    // Desasigna un material de la zona (lo deja sin ubicacion asignada)
    @Auditable(entidad = "materiales", operacion = "UPDATE")
    @Transactional
    public MaterialResponse desasignarMaterial(Integer idZona, Integer idMaterial) {
        obtenerActiva(idZona);
        Material material = obtenerMaterialActivo(idMaterial);
        if (material.getZona() == null || !material.getZona().getIdZona().equals(idZona)) {
            throw new ApiException(HttpStatus.CONFLICT, "El material no esta asignado a esta zona.");
        }
        material.setZona(null);
        return MaterialMapper.toResponse(materialRepository.save(material));
    }

    // Helper: valida que los materiales activos asignados sigan siendo compatibles con el
    // nuevo tipo permitido de la zona (el cambio solo es válido para todos o ninguno)
    private void validarCompatibilidadMateriales(Integer idZona, String nuevoTipo) {
        List<Material> asignados = materialRepository.findAllByActivoTrueAndZona_IdZona(idZona);
        if (!StringUtils.hasText(nuevoTipo)) {
            return;
        }
        List<String> incompatibles = asignados.stream()
                .filter(m -> !nuevoTipo.equalsIgnoreCase(m.getCategoria().getNombre()))
                .map(Material::getNombre)
                .toList();
        if (!incompatibles.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "La zona tiene materiales incompatibles con el nuevo tipo: " + String.join(", ", incompatibles)
                            + ". Desasignelos antes de cambiar el tipo (RF-015).");
        }
    }

    // Helper: carga una zona activa o lanza 404
    private ZonaAcopio obtenerActiva(Integer id) {
        return zonaRepository.findByIdZonaAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Zona no encontrada."));
    }

    // Helper: carga un material activo o lanza 404
    private Material obtenerMaterialActivo(Integer id) {
        return materialRepository.findByIdMaterialAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
    }

    // Paginacion basica: page >= 0 y size entre 1 y MAX_PAGE_SIZE. Orden por defecto
    // por nombre de zona asc (mismo criterio del listado de proveedores/materiales)
    private Pageable validarPaginacion(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El parametro 'page' no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El parametro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by("nombreZona"));
    }

    // Helper: limpia textos opcionales ("" o espacios -> null)
    private String trimable(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    // Helper: compara dos textos opcionales ignorando mayusculas/minusculas
    private boolean igualesSinMayusculas(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        return a != null && a.equalsIgnoreCase(b);
    }
}