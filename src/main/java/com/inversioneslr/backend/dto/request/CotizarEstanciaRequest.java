package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Datos para cotizar una estadía. No reserva ni guarda nada.")
public record CotizarEstanciaRequest(

        @Schema(description = "Identificador de la publicación que se cotiza", example = "12")
        @NotNull(message = "La publicación es obligatoria")
        @Positive(message = "La publicación debe tener un identificador mayor que 0")
        Long idPublicacion,

        @Schema(description = "Fecha de llegada, en formato AAAA-MM-DD; no puede ser anterior a hoy", example = "2030-06-10")
        @NotNull(message = "La fecha de llegada es obligatoria")
        LocalDate fechaLlegada,

        @Schema(description = "Fecha de salida, posterior a la llegada, en formato AAAA-MM-DD; esa noche no se cobra",
                example = "2030-06-13")
        @NotNull(message = "La fecha de salida es obligatoria")
        LocalDate fechaSalida,

        @Schema(description = "Cantidad de huéspedes. Es opcional: si se envía, no puede superar la capacidad del "
                + "alojamiento. No cambia el precio", example = "2")
        @Positive(message = "La cantidad de huéspedes debe ser mayor que 0")
        Integer huespedes,

        @Schema(description = "Nombres de los servicios adicionales de la publicación que el Huésped quiere incluir. "
                + "Es opcional; no se admiten repetidos", example = "[\"Desayuno\", \"Traslado al aeropuerto\"]")
        @Size(max = CotizarEstanciaRequest.MAXIMO_SERVICIOS,
                message = "Puede elegir hasta " + CotizarEstanciaRequest.MAXIMO_SERVICIOS + " servicios adicionales")
        List<@NotBlank(message = "El nombre del servicio adicional no puede estar vacío")
                @Size(max = CotizarEstanciaRequest.LONGITUD_MAXIMA_NOMBRE,
                        message = "El nombre del servicio adicional admite hasta "
                                + CotizarEstanciaRequest.LONGITUD_MAXIMA_NOMBRE + " caracteres") String> serviciosAdicionales
) {

    /** Tope razonable de servicios por cotización: acota la consulta a la base. */
    static final int MAXIMO_SERVICIOS = 20;

    /** Longitud de la columna servicio_adicional.nombre. */
    static final int LONGITUD_MAXIMA_NOMBRE = 100;

    @AssertTrue(message = "La fecha de salida debe ser posterior a la fecha de llegada")
    @Schema(hidden = true)
    public boolean isRangoFechasValido() {
        return fechaLlegada == null
                || fechaSalida == null
                || fechaSalida.isAfter(fechaLlegada);
    }
}
