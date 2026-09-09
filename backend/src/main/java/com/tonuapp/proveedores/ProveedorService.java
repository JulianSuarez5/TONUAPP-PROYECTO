package com.tonuapp.proveedores;

import com.tonuapp.audit.Auditable;
import com.tonuapp.domain.Material;
import com.tonuapp.domain.MaterialProveedor;
import com.tonuapp.domain.Proveedor;
import com.tonuapp.proveedores.dto.MaterialProveedorRequest;
import com.tonuapp.proveedores.dto.MaterialProveedorResponse;
import com.tonuapp.proveedores.dto.ProveedorRequest;
import com.tonuapp.proveedores.dto.ProveedorResponse;
import com.tonuapp.repository.MaterialProveedorRepository;
import com.tonuapp.repository.MaterialRepository;
import com.tonuapp.repository.ProveedorRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRUD de proveedores y gestion de la relacion N:M con materiales (RF-010, D-01/D-14).
 * Reglas:
 *  - NIT unico;
 *  - no se elimina (soft delete) un proveedor con materiales asociados (RF-010);
 *  - es_principal es "por material": un material tiene a lo sumo un proveedor principal,
 *    y asignar uno nuevo desmarca al anterior;
 *  - soft delete: "eliminar" = activo = false (D-04).
 */
@Service
public class ProveedorService {

    /** Tamano maximo de pagina que acepta el listado (evita consultas sin tope). */
    public static final int MAX_PAGE_SIZE = 100;

    private final ProveedorRepository proveedorRepository;
    private final MaterialProveedorRepository materialProveedorRepository;
    private final MaterialRepository materialRepository;

    public ProveedorService(ProveedorRepository proveedorRepository,
                            MaterialProveedorRepository materialProveedorRepository,
                            MaterialRepository materialRepository) {
        this.proveedorRepository = proveedorRepository;
        this.materialProveedorRepository = materialProveedorRepository;
        this.materialRepository = materialRepository;
    }

    // Lista paginada de proveedores activos con la cantidad de materiales asociados (RF-010).
    // El filtro opcional q busca por nombre o NIT (contiene, sin distinguir mayusculas):
    // se agrego por consistencia de UX con la busqueda de materiales (RF-003), aunque
    // RF-010/US-29 solo exigen consultar el listado. El conteo se limita a los proveedores
    // de la pagina (no carga toda la tabla N:M)
    public PagedResponse<ProveedorResponse> listarActivos(String q, int page, int size) {
        Pageable pageable = validarPaginacion(page, size);
        Page<Proveedor> pagina = proveedorRepository.findAll(filtros(q), pageable);
        Set<Integer> ids = pagina.getContent().stream()
                .map(Proveedor::getIdProveedor)
                .collect(Collectors.toSet());
        Map<Integer, Long> conteo = materialProveedorRepository.findByProveedorIdIn(ids).stream()
                .collect(Collectors.groupingBy(MaterialProveedor::getProveedorId, Collectors.counting()));
        return PagedResponse.of(pagina.map(p -> ProveedorMapper.toResponse(p, conteo.getOrDefault(p.getIdProveedor(), 0L))));
    }

    // Predicados del listado: activos siempre; si q viene con texto, nombre o NIT lo contienen
    // (LIKE en minusculas para igualar sin distinguir mayusculas)
    private Specification<Proveedor> filtros(String q) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isTrue(root.get("activo")));
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("nit")), like)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    // Devuelve un proveedor activo por id, o 404
    public ProveedorResponse obtener(Integer id) {
        Proveedor p = obtenerActivo(id);
        return ProveedorMapper.toResponse(p, materialProveedorRepository.countByProveedorId(id));
    }

    // Crea un proveedor validando NIT unico (RF-010)
    @Auditable(entidad = "proveedores", operacion = "CREATE")
    @Transactional
    public ProveedorResponse crear(ProveedorRequest request) {
        String nombre = request.nombre().trim();
        String nit = request.nit().trim();
        if (proveedorRepository.existsByNit(nit)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un proveedor con ese NIT.");
        }

        Proveedor p = new Proveedor();
        p.setNombre(nombre);
        p.setNit(nit);
        p.setContacto(request.contacto() != null ? request.contacto().trim() : null);
        p.setTelefono(request.telefono() != null ? request.telefono().trim() : null);
        p.setUbicacion(request.ubicacion() != null ? request.ubicacion().trim() : null);
        p.setActivo(true);
        p.setFechaRegistro(LocalDateTime.now());
        return ProveedorMapper.toResponse(proveedorRepository.save(p), 0L);
    }

    // Actualiza los datos de un proveedor (RF-010: solo datos, no el id); NIT unico
    // excluyendo el propio proveedor
    @Auditable(entidad = "proveedores", operacion = "UPDATE")
    @Transactional
    public ProveedorResponse actualizar(Integer id, ProveedorRequest request) {
        Proveedor p = obtenerActivo(id);
        String nombre = request.nombre().trim();
        String nit = request.nit().trim();
        if (proveedorRepository.existsByNit(nit) && !p.getNit().equalsIgnoreCase(nit)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe un proveedor con ese NIT.");
        }

        p.setNombre(nombre);
        p.setNit(nit);
        p.setContacto(request.contacto() != null ? request.contacto().trim() : null);
        p.setTelefono(request.telefono() != null ? request.telefono().trim() : null);
        p.setUbicacion(request.ubicacion() != null ? request.ubicacion().trim() : null);
        long cantidad = materialProveedorRepository.countByProveedorId(id);
        return ProveedorMapper.toResponse(proveedorRepository.save(p), cantidad);
    }

    // Soft delete (D-04). Bloqueado si el proveedor tiene materiales asociados (RF-010)
    @Auditable(entidad = "proveedores", operacion = "DELETE")
    @Transactional
    public void desactivar(Integer id) {
        Proveedor p = obtenerActivo(id);
        if (materialProveedorRepository.countByProveedorId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "No se puede eliminar un proveedor con materiales asociados (RF-010).");
        }
        p.setActivo(false);
        proveedorRepository.save(p);
    }

    // Materiales asociados a un proveedor (N:M), con nombre del material resuelto
    public List<MaterialProveedorResponse> listarMaterialesDeProveedor(Integer proveedorId) {
        Proveedor proveedor = obtenerActivo(proveedorId);
        List<MaterialProveedor> filas = materialProveedorRepository.findByProveedorId(proveedorId);
        if (filas.isEmpty()) {
            return List.of();
        }
        Set<Integer> ids = filas.stream().map(MaterialProveedor::getMaterialId).collect(Collectors.toSet());
        Map<Integer, Material> materiales = materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Material::getIdMaterial, Function.identity()));
        return filas.stream()
                .map(fila -> ProveedorMapper.toMaterialProveedorResponse(fila, materiales.get(fila.getMaterialId()), proveedor))
                .toList();
    }

    // Proveedores asociados a un material (N:M), se requiere que el material exista y este activo
    public List<MaterialProveedorResponse> listarProveedoresDeMaterial(Integer materialId) {
        Material material = materialRepository.findByIdMaterialAndActivoTrue(materialId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
        List<MaterialProveedor> filas = materialProveedorRepository.findByMaterialId(materialId);
        if (filas.isEmpty()) {
            return List.of();
        }
        Set<Integer> ids = filas.stream().map(MaterialProveedor::getProveedorId).collect(Collectors.toSet());
        Map<Integer, Proveedor> proveedores = proveedorRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Proveedor::getIdProveedor, Function.identity()));
        return filas.stream()
                .map(fila -> ProveedorMapper.toMaterialProveedorResponse(fila, material, proveedores.get(fila.getProveedorId())))
                .toList();
    }

    // Asocia un material a un proveedor (N:M). 409 si ya estaban asociados; si es_principal
    // es true, desmarca el principal anterior de ese material
    @Auditable(entidad = "material_proveedor", operacion = "CREATE")
    @Transactional
    public MaterialProveedorResponse asociarMaterial(Integer proveedorId, MaterialProveedorRequest request) {
        Proveedor proveedor = obtenerActivo(proveedorId);
        Material material = obtenerMaterialActivo(request.idMaterial());
        if (materialProveedorRepository.existsByProveedorIdAndMaterialId(proveedorId, request.idMaterial())) {
            throw new ApiException(HttpStatus.CONFLICT, "El material ya esta asociado a este proveedor.");
        }

        boolean principal = Boolean.TRUE.equals(request.esPrincipal());
        if (principal) {
            desmarcarPrincipalDelMaterial(request.idMaterial());
        }

        MaterialProveedor fila = new MaterialProveedor();
        fila.setMaterialId(request.idMaterial());
        fila.setProveedorId(proveedorId);
        fila.setEsPrincipal(principal);
        fila.setFechaAsociacion(LocalDateTime.now());
        materialProveedorRepository.save(fila);
        return ProveedorMapper.toMaterialProveedorResponse(fila, material, proveedor);
    }

    // Cambia es_principal de una asociacion existente (si pasa a true, desmarca el anterior)
    @Auditable(entidad = "material_proveedor", operacion = "UPDATE")
    @Transactional
    public MaterialProveedorResponse actualizarPrincipal(Integer proveedorId, Integer materialId, boolean esPrincipal) {
        Proveedor proveedor = obtenerActivo(proveedorId);
        Material material = obtenerMaterialActivo(materialId);
        MaterialProveedor fila = materialProveedorRepository.findByProveedorIdAndMaterialId(proveedorId, materialId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "El material no esta asociado a este proveedor."));

        if (esPrincipal) {
            desmarcarPrincipalDelMaterial(materialId);
        }
        fila.setEsPrincipal(esPrincipal);
        materialProveedorRepository.save(fila);
        return ProveedorMapper.toMaterialProveedorResponse(fila, material, proveedor);
    }

    // Quita la asociacion material-proveedor
    @Auditable(entidad = "material_proveedor", operacion = "DELETE")
    @Transactional
    public void desasociarMaterial(Integer proveedorId, Integer materialId) {
        obtenerActivo(proveedorId);
        obtenerMaterialActivo(materialId);
        MaterialProveedor fila = materialProveedorRepository.findByProveedorIdAndMaterialId(proveedorId, materialId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "El material no esta asociado a este proveedor."));
        materialProveedorRepository.delete(fila);
    }

    // Helper: carga un proveedor activo o lanza 404
    private Proveedor obtenerActivo(Integer id) {
        return proveedorRepository.findByIdProveedorAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Proveedor no encontrado."));
    }

    // Paginacion basica: page >= 0 y size entre 1 y MAX_PAGE_SIZE. Orden por defecto
    // por nombre asc (mismo criterio del listado de materiales)
    private Pageable validarPaginacion(int page, int size) {
        if (page < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El parametro 'page' no puede ser negativo.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "El parametro 'size' debe estar entre 1 y " + MAX_PAGE_SIZE + ".");
        }
        return PageRequest.of(page, size, Sort.by("nombre"));
    }

    // Helper: carga un material activo o lanza 404
    private Material obtenerMaterialActivo(Integer id) {
        return materialRepository.findByIdMaterialAndActivoTrue(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Material no encontrado."));
    }

    // Helper: deja sin proveedor principal al material (nadie marca dos principales a la vez)
    private void desmarcarPrincipalDelMaterial(Integer materialId) {
        List<MaterialProveedor> anteriores = materialProveedorRepository.findByMaterialIdAndEsPrincipalTrue(materialId);
        anteriores.forEach(fila -> fila.setEsPrincipal(false));
        materialProveedorRepository.saveAll(anteriores);
    }
}