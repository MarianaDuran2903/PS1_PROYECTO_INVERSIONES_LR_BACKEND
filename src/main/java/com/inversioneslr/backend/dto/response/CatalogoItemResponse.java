package com.inversioneslr.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Elemento de un catálogo (ciudad, tipo de alojamiento, política)")
public record CatalogoItemResponse(

        @Schema(description = "Identificador", example = "3")
        Long id,

        @Schema(description = "Nombre", example = "Cartagena")
        String nombre
) {
}
