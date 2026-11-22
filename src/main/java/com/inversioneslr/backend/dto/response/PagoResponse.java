package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.model.enums.EstadoReserva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Pago de una reserva: el estado del cobro y de la reserva. Un pago se confirma solo con el "
        + "resultado de la pasarela, nunca con la página a la que vuelve el Huésped")
public record PagoResponse(
        Long idPago,
        Long idReserva,
        @Schema(description = "PENDIENTE: la sesión espera el pago. APROBADO: cobrado. RECHAZADO: un intento falló y "
                + "la sesión sigue abierta para reintentar. EXPIRADO: la sesión venció sin pago")
        EstadoPago estado,
        @Schema(description = "Estado de la reserva; pasa a CONFIRMADA solo cuando el pago queda APROBADO")
        EstadoReserva estadoReserva,
        @Schema(description = "Lo que se cobra, en pesos: el total de la reserva")
        BigDecimal monto,
        String moneda,
        @Schema(description = "Página de pago de Stripe. Solo viene mientras la sesión admite el pago (estado "
                + "PENDIENTE o RECHAZADO y sin vencer); en otro caso es nulo")
        String urlPago,
        @Schema(description = "Hasta cuándo la sesión admite el pago")
        OffsetDateTime fechaExpiracion,
        @Schema(description = "Código del rechazo, solo si el estado es RECHAZADO", example = "card_declined")
        String codigoRechazo,
        @Schema(description = "Mensaje para mostrar al Huésped, solo si el estado es RECHAZADO",
                example = "Su tarjeta fue rechazada. Intente con otra tarjeta.")
        String motivoRechazo
) {
}
