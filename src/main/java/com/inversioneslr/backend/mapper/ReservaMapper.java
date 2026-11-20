package com.inversioneslr.backend.mapper;

import com.inversioneslr.backend.dto.response.ReservaResponse;
import com.inversioneslr.backend.model.Reserva;
import org.springframework.stereotype.Component;

@Component
public class ReservaMapper {

    public ReservaResponse toResponse(Reserva reserva) {
        return new ReservaResponse(
                reserva.getIdReserva(),
                reserva.getPublicacion().getIdPublicacion(),
                reserva.getFechaLlegada(),
                reserva.getFechaSalida(),
                reserva.noches(),
                reserva.getNumeroHuespedes(),
                reserva.getEstado(),
                reserva.getFechaExpiracion(),
                new ReservaResponse.PrecioResponse(
                        reserva.getPrecioBase(),
                        reserva.getPorcentajeDescuentoAplicado(),
                        reserva.getValorDescuento(),
                        reserva.getTarifaLimpieza(),
                        reserva.getPorcentajeTarifaServicio(),
                        reserva.getTarifaServicio(),
                        reserva.getTotal()),
                new ReservaResponse.PoliticaAplicadaResponse(
                        reserva.getHorasAnticipacionAplicada(),
                        reserva.getPorcentajeDevolucionAplicado()));
    }
}
