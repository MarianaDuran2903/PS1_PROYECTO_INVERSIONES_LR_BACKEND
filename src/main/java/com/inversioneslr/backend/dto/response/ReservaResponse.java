package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.EstadoReserva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Schema(description = "Reserva con el precio y la política de cancelación vigentes al crearla")
public record ReservaResponse(
        Long idReserva,
        Long idPublicacion,
        LocalDate fechaLlegada,
        LocalDate fechaSalida,
        long noches,
        Integer huespedes,
        EstadoReserva estado,
        @Schema(description = "Hasta cuándo espera el pago una reserva PENDIENTE_PAGO; después vence y libera las fechas")
        OffsetDateTime fechaExpiracion,
        PrecioResponse precio,
        PoliticaAplicadaResponse politicaCancelacion
) {

    @Schema(description = "Desglose del precio, en pesos")
    public record PrecioResponse(
            BigDecimal precioBase,
            @Schema(description = "Descuento por estadía larga aplicado, en porcentaje; 0 si no corresponde")
            BigDecimal porcentajeDescuento,
            BigDecimal valorDescuento,
            BigDecimal tarifaLimpieza,
            @Schema(description = "Tarifa de servicio de la plataforma, en porcentaje sobre la base menos el descuento")
            BigDecimal porcentajeTarifaServicio,
            BigDecimal tarifaServicio,
            BigDecimal total
    ) {
    }

    @Schema(description = "Política de cancelación que regía al reservar")
    public record PoliticaAplicadaResponse(
            Integer horasAnticipacionMin,
            BigDecimal porcentajeDevolucion
    ) {
    }
}
