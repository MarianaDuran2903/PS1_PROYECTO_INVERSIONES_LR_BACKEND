package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.TipoCobro;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "Cotización de una estadía: lo que pagaría el Huésped si reservara ahora. No es una reserva "
        + "y no bloquea las fechas")
public record CotizacionResponse(
        Long idPublicacion,
        LocalDate fechaLlegada,
        LocalDate fechaSalida,
        long noches,
        PrecioCotizacionResponse precio
) {

    @Schema(description = "Desglose del precio, en pesos")
    public record PrecioCotizacionResponse(
            @Schema(description = "Precio de una noche")
            BigDecimal precioNoche,
            @Schema(description = "Precio de una noche por las noches, antes del descuento")
            BigDecimal precioBase,
            @Schema(description = "Descuento por estadía larga aplicado, en porcentaje; 0 si no corresponde")
            BigDecimal porcentajeDescuento,
            BigDecimal valorDescuento,
            BigDecimal tarifaLimpieza,
            @Schema(description = "Servicios adicionales elegidos, en el orden pedido; vacío si no se eligió ninguno")
            List<ServicioAdicionalResponse> serviciosAdicionales,
            BigDecimal totalServiciosAdicionales,
            @Schema(description = "Tarifa de servicio de la plataforma, en porcentaje sobre la base menos el descuento")
            BigDecimal porcentajeTarifaServicio,
            BigDecimal tarifaServicio,
            @Schema(description = "Base menos descuento, más limpieza, servicios adicionales y tarifa de servicio")
            BigDecimal total
    ) {
    }

    @Schema(description = "Un servicio adicional incluido en la cotización")
    public record ServicioAdicionalResponse(
            String nombre,
            TipoCobro tipoCobro,
            @Schema(description = "Precio unitario: por noche o por estadía según el tipo de cobro")
            BigDecimal precio,
            @Schema(description = "Lo que cuesta en esta estadía")
            BigDecimal valor
    ) {
    }
}
