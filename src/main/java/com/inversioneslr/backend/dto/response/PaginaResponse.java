package com.inversioneslr.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

@Schema(description = "Página de resultados")
public record PaginaResponse<T>(

        @Schema(description = "Elementos de la página (vacío si no hay resultados)")
        List<T> contenido,

        @Schema(description = "Número de página, empezando en 0", example = "0")
        int pagina,

        @Schema(description = "Tamaño de página pedido", example = "20")
        int tamano,

        @Schema(description = "Total de elementos en todas las páginas", example = "3")
        long totalElementos,

        @Schema(description = "Total de páginas", example = "1")
        int totalPaginas,

        @Schema(description = "Aviso para el usuario cuando no hay ningún resultado; nulo en otro caso",
                example = "No tiene publicaciones registradas", nullable = true)
        String mensaje
) {

    /**
     * Convierte una página de entidades; el mensaje solo se incluye cuando no existe ningún elemento.
     */
    public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversion, String mensajeSinResultados) {
        return new PaginaResponse<>(
                pagina.getContent().stream().map(conversion).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages(),
                pagina.getTotalElements() == 0 ? mensajeSinResultados : null);
    }
}
