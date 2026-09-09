package com.tonuapp.catalogos;

import com.tonuapp.repository.CategoriaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de categorias (D-15). Solo lectura; la administracion de catalogos se
 * implementa en la fase de configuracion. Cualquier usuario autenticado puede consultarlo.
 */
@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaRepository categoriaRepository;

    public CategoriaController(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    // Lista las categorias activas (para dropdowns del frontend)
    @GetMapping
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(c -> new CategoriaResponse(c.getIdCategoria(), c.getNombre(), c.isActivo()))
                .toList();
    }
}