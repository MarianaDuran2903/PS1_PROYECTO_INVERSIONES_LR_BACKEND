package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Schema(description = "Filtros opcionales para la consulta pública de alojamientos")
public record FiltroAlojamientosRequest(

        @Schema(description = "Identificador de la ciudad seleccionada")
        @Positive(message = "La ciudad debe tener un identificador mayor que 0")
        Long idCiudad,

        @Schema(description = "Fecha de llegada, en formato AAAA-MM-DD")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fechaLlegada,

        @Schema(description = "Fecha de salida, posterior a la llegada, en formato AAAA-MM-DD")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fechaSalida,

        @Schema(description = "Cantidad de huéspedes que debe admitir el alojamiento")
        @Positive(message = "La cantidad de huéspedes debe ser mayor que 0")
        Integer huespedes
) {

    @AssertTrue(message = "Debe indicar tanto la fecha de llegada como la fecha de salida")
    @Schema(hidden = true)
    public boolean isFechasCompletas() {
        return (fechaLlegada == null) == (fechaSalida == null);
    }

    @AssertTrue(message = "La fecha de salida debe ser posterior a la fecha de llegada")
    @Schema(hidden = true)
    public boolean isRangoFechasValido() {
        return fechaLlegada == null
                || fechaSalida == null
                || fechaSalida.isAfter(fechaLlegada);
    }
}