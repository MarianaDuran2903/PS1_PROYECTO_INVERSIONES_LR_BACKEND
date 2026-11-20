package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

@Schema(description = "Datos para reservar un alojamiento. El Huésped es siempre el del token, no un dato del cuerpo.")
public record CrearReservaRequest(

        @Schema(description = "Identificador de la publicación que se reserva", example = "12")
        @NotNull(message = "La publicación es obligatoria")
        @Positive(message = "La publicación debe tener un identificador mayor que 0")
        Long idPublicacion,

        @Schema(description = "Fecha de llegada, en formato AAAA-MM-DD; no puede ser anterior a hoy", example = "2030-06-10")
        @NotNull(message = "La fecha de llegada es obligatoria")
        LocalDate fechaLlegada,

        @Schema(description = "Fecha de salida, posterior a la llegada, en formato AAAA-MM-DD; esa noche no se ocupa",
                example = "2030-06-13")
        @NotNull(message = "La fecha de salida es obligatoria")
        LocalDate fechaSalida,

        @Schema(description = "Cantidad de huéspedes; no puede superar la capacidad del alojamiento", example = "2")
        @NotNull(message = "La cantidad de huéspedes es obligatoria")
        @Positive(message = "La cantidad de huéspedes debe ser mayor que 0")
        Integer huespedes
) {

    @AssertTrue(message = "La fecha de salida debe ser posterior a la fecha de llegada")
    @Schema(hidden = true)
    public boolean isRangoFechasValido() {
        return fechaLlegada == null
                || fechaSalida == null
                || fechaSalida.isAfter(fechaLlegada);
    }
}
