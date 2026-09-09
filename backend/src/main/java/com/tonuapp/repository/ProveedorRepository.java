package com.tonuapp.repository;

import com.tonuapp.domain.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer>,
        JpaSpecificationExecutor<Proveedor> {

    // Busca un proveedor activo por id (para CRUD de proveedores)
    Optional<Proveedor> findByIdProveedorAndActivoTrue(Integer idProveedor);

    // True si el NIT ya esta registrado (RF-010: NIT unico)
    boolean existsByNit(String nit);

    // Carga varios proveedores por id (para resolver los materiales asociados en N:M)
    List<Proveedor> findAllByIdProveedorInOrderByNombreAsc(Collection<Integer> ids);
}