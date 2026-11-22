package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Información mínima para crear una publicación. El Anfitrión, el estado y la política no viajan
 * en el cuerpo: los fija el servidor.
 */
@Schema(description = "Datos para crear una publicación en estado BORRADOR")
public record CrearPublicacionRequest(

        @Schema(description = "Título de la publicación", example = "Apartamento con vista al mar", maxLength = 150)
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 150, message = "El título admite máximo 150 caracteres")
        String titulo,

        @Schema(description = "Descripción del alojamiento", example = "Dos habitaciones, cocina equipada y balcón.",
                maxLength = 1000)
        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 1000, message = "La descripción admite máximo 1000 caracteres")
        String descripcion,

        @Schema(description = "Ubicación textual (dirección)", example = "Calle 5 # 3-20", maxLength = 250)
        @NotBlank(message = "La dirección es obligatoria")
        @Size(max = 250, message = "La dirección admite máximo 250 caracteres")
        String direccionTextual,

        @Schema(description = "Ciudad (parte de la ubicación); ver GET /api/v1/ciudades", example = "3")
        @NotNull(message = "La ciudad es obligatoria")
        @Positive(message = "La ciudad no es válida")
        Long idCiudad,

        @Schema(description = "Barrio o zona (opcional)", example = "Bocagrande", maxLength = 120,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 120, message = "El barrio o zona admite máximo 120 caracteres")
        String barrioZona,

        @Schema(description = "Tipo de alojamiento; ver GET /api/v1/tipos-alojamiento", example = "1")
        @NotNull(message = "El tipo de alojamiento es obligatorio")
        @Positive(message = "El tipo de alojamiento no es válido")
        Long idTipo,

        @Schema(description = "Número máximo de huéspedes (entero mayor que 0)", example = "4", minimum = "1")
        @NotNull(message = "La capacidad es obligatoria")
        @Positive(message = "La capacidad debe ser un número entero mayor que 0")
        Integer capacidad,

        @Schema(description = "Precio por noche (mayor que 0, máximo 2 decimales)", example = "250000.00")
        @NotNull(message = "El precio por noche es obligatorio")
        @DecimalMin(value = "0", inclusive = false, message = "El precio por noche debe ser mayor que 0")
        @Digits(integer = 10, fraction = 2,
                message = "El precio por noche admite máximo 10 dígitos enteros y 2 decimales")
        BigDecimal precioNoche
) {
}
