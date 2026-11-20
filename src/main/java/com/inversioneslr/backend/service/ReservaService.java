package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.CrearReservaRequest;
import com.inversioneslr.backend.dto.response.ReservaResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

/**
 * Reservas de alojamientos por el Huésped.
 */
public interface ReservaService {

    /**
     * Crea una reserva PENDIENTE_PAGO para el Huésped autenticado, con el precio y la política de cancelación
     * vigentes, y ocupa las fechas en el calendario. Si ese Huésped ya envió la misma clave de idempotencia con
     * la misma solicitud, devuelve la reserva que se creó la primera vez en lugar de crear otra.
     *
     * @throws com.inversioneslr.backend.exception.DatosReservaInvalidosException        fechas pasadas o más
     *                                                                                   huéspedes que la capacidad
     * @throws com.inversioneslr.backend.exception.AlojamientoNoDisponibleException      la publicación no existe o
     *                                                                                   no está ACTIVA
     * @throws com.inversioneslr.backend.exception.FechasNoDisponiblesException          alguna noche no está libre
     * @throws com.inversioneslr.backend.exception.ClaveIdempotenciaReutilizadaException la clave ya se usó con otra
     *                                                                                   solicitud
     */
    ResultadoCreacion crear(CrearReservaRequest request, String claveIdempotencia, UsuarioAutenticado huesped);

    /**
     * La reserva y si esta llamada la creó ({@code true}) o es la repetición de una anterior ({@code false}).
     */
    record ResultadoCreacion(ReservaResponse reserva, boolean creada) {
    }
}
