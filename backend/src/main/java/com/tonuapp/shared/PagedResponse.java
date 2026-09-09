package com.tonuapp.shared;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Rspuesta paginada generica para los listados que la UI pagina (materiales,
 * movimientos). Expone el contenido de la pagina actual junto con los metadatos
 * necesarios para los controles de paginacion (total de elementos y de paginas).
 * Evita exponer el objeto {@link Page} de Spring Data (JSON verboso y acoplado).
 *
 * @param content       elementos de la pagina actual
 * @param totalElements total de elementos que satisfacen el filtro
 * @param totalPages    total de paginas (>= 0 si no hay resultados)
 * @param page          indice de la pagina solicitada (0-based)
 * @param size          tamano de pagina solicitado
 * @param <T>           tipo de los elementos de la pagina
 */
public record PagedResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {

    // Construye la respuesta a partir de una pagina de Spring Data
    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize());
    }
}
