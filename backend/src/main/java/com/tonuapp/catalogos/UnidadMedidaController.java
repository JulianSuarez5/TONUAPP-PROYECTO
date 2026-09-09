package com.tonuapp.catalogos;

import com.tonuapp.repository.UnidadMedidaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de unidades de medida (D-16). Solo lectura; la administracion de catalogos se
 * implementa en la fase de configuracion. Cualquier usuario autenticado puede consultarlo.
 */
@RestController
@RequestMapping("/api/unidades-medida")
public class UnidadMedidaController {

    private final UnidadMedidaRepository unidadMedidaRepository;

    public UnidadMedidaController(UnidadMedidaRepository unidadMedidaRepository) {
        this.unidadMedidaRepository = unidadMedidaRepository;
    }

    // Lista las unidades de medida (para dropdowns del frontend)
    @GetMapping
    public List<UnidadMedidaResponse> listar() {
        return unidadMedidaRepository.findAllByOrderByNombreAsc().stream()
                .map(u -> new UnidadMedidaResponse(u.getIdUnidad(), u.getNombre(), u.getAbreviatura()))
                .toList();
    }
}