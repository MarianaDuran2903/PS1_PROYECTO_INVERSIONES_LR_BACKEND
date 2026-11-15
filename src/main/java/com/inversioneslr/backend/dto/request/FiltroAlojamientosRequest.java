package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        Integer huespedes,

        @Schema(description = "Identificador del tipo de alojamiento")
        @Positive(message = "El tipo de alojamiento debe tener un identificador mayor que 0")
        Long idTipo,

        @Schema(description = "Precio mínimo por noche, incluido")
        @DecimalMin(value = "0", message = "El precio mínimo debe ser 0 o mayor")
        @Digits(integer = 10, fraction = 2,
                message = "El precio mínimo admite hasta 10 enteros y 2 decimales")
        BigDecimal precioMinimo,

        @Schema(description = "Precio máximo por noche, incluido")
        @DecimalMin(value = "0", message = "El precio máximo debe ser 0 o mayor")
        @Digits(integer = 10, fraction = 2,
                message = "El precio máximo admite hasta 10 enteros y 2 decimales")
        BigDecimal precioMaximo,

        @Schema(description = "Identificadores de todos los servicios requeridos")
        List<
                @NotNull(message = "El servicio debe tener un identificador")
                @Positive(message = "El servicio debe tener un identificador mayor que 0")
                        Long> idsServicios
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

    @AssertTrue(message = "El precio máximo debe ser mayor o igual al precio mínimo")
    @Schema(hidden = true)
    public boolean isRangoPreciosValido() {
        return precioMinimo == null
                || precioMaximo == null
                || precioMaximo.compareTo(precioMinimo) >= 0;
    }
}