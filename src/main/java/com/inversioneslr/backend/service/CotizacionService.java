package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.request.CotizarEstanciaRequest;
import com.inversioneslr.backend.dto.response.CotizacionResponse;

public interface CotizacionService {

    /**
     * Calcula lo que costaría la estadía pedida, sin reservarla ni guardar nada.
     *
     * @throws com.inversioneslr.backend.exception.DatosReservaInvalidosException  si la llegada es anterior a hoy,
     *         los huéspedes superan la capacidad o algún servicio adicional no es de la publicación o se repite
     * @throws com.inversioneslr.backend.exception.AlojamientoNoDisponibleException si la publicación no existe o no
     *         está activa
     * @throws com.inversioneslr.backend.exception.CotizacionNoDisponibleException  si alguna noche no está disponible
     */
    CotizacionResponse cotizar(CotizarEstanciaRequest request);
}
