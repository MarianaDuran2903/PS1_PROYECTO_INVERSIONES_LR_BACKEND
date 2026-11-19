package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Resumen de una publicación para los listados")
public record PublicacionResumenResponse(

        @Schema(description = "Identificador de la publicación", example = "12")
        Long idPublicacion,

        @Schema(description = "Título", example = "Apartamento con vista al mar")
        String titulo,

        @Schema(description = "Estado vigente", example = "BORRADOR")
        EstadoPublicacion estado,

        @Schema(description = "Ciudad")
        CatalogoItemResponse ciudad,

        @Schema(description = "Tipo de alojamiento")
        CatalogoItemResponse tipo,

        @Schema(description = "Capacidad máxima de huéspedes", example = "4")
        Integer capacidad,

        @Schema(description = "Precio por noche", example = "250000.00")
        BigDecimal precioNoche
) {
}
