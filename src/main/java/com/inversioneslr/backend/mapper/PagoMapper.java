package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.PagoResponse;
import com.inversioneslr.backend.model.MotivoRechazoPago;
import com.inversioneslr.backend.model.Pago;
import com.inversioneslr.backend.model.Reserva;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class PagoMapper {

    /**
     * La URL de pago solo se entrega mientras la sesión admite el pago; después ya no sirve y no se expone. El
     * mensaje del rechazo se traduce del código de Stripe: nunca se devuelve su texto original.
     */
    public PagoResponse toResponse(Pago pago, OffsetDateTime ahora) {
        Reserva reserva = pago.getReserva();
        return new PagoResponse(
                pago.getIdPago(),
                reserva.getIdReserva(),
                pago.getEstado(),
                reserva.getEstado(),
                pago.getMonto(),
                pago.getMoneda(),
                pago.sesionVigente(ahora) ? pago.getUrlPago() : null,
                pago.getFechaExpiracion(),
                pago.getCodigoRechazo(),
                MotivoRechazoPago.mensajePara(pago.getCodigoRechazo()));
    }
}
