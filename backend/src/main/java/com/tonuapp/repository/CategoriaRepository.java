package com.tonuapp.repository;

import com.tonuapp.domain.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {

    // Lista las categorias activas ordenadas por nombre (dropdown del frontend)
    List<Categoria> findByActivoTrueOrderByNombreAsc();
}