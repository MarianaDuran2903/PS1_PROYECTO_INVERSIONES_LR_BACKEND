package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Publicación de un alojamiento")
public record PublicacionResponse(

        @Schema(description = "Identificador de la publicación", example = "12")
        Long idPublicacion,

        @Schema(description = "Anfitrión propietario", example = "5")
        Long idAnfitrion,

        @Schema(description = "Estado vigente", example = "BORRADOR")
        EstadoPublicacion estado,

        @Schema(description = "Título", example = "Apartamento con vista al mar")
        String titulo,

        @Schema(description = "Descripción", example = "Dos habitaciones, cocina equipada y balcón.")
        String descripcion,

        @Schema(description = "Ubicación textual", example = "Calle 5 # 3-20")
        String direccionTextual,

        @Schema(description = "Barrio o zona (nulo si no se registró)", example = "Bocagrande", nullable = true)
        String barrioZona,

        @Schema(description = "Ciudad")
        CatalogoItemResponse ciudad,

        @Schema(description = "Tipo de alojamiento")
        CatalogoItemResponse tipo,

        @Schema(description = "Política de cancelación")
        CatalogoItemResponse politica,

        @Schema(description = "Capacidad máxima de huéspedes", example = "4")
        Integer capacidad,

        @Schema(description = "Precio por noche", example = "250000.00")
        BigDecimal precioNoche,

        @Schema(description = "Tarifa de limpieza", example = "0.00")
        BigDecimal tarifaLimpieza
) {
}
