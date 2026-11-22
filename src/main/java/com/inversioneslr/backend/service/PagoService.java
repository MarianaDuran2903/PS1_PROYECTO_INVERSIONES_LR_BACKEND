package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.response.PagoResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

/**
 * Pago de una reserva con Stripe en modo de pruebas.
 */
public interface PagoService {

    /**
     * El pago iniciado y si se creó ahora o ya existía una sesión abierta que se devuelve tal cual.
     */
    record ResultadoInicio(PagoResponse pago, boolean creado) {
    }

    /**
     * Abre la sesión de pago de una reserva PENDIENTE_PAGO del Huésped, o devuelve la que ya tiene abierta. El
     * monto es el de la reserva. Mientras el Huésped paga, el plazo de la reserva se alarga para que no venza en
     * medio del pago.
     */
    ResultadoInicio iniciar(Long idReserva, UsuarioAutenticado huesped);

    /**
     * El pago más reciente de una reserva del Huésped. Si sigue abierto, consulta antes a la pasarela por si el
     * resultado todavía no llegó por aviso; si la pasarela no responde, devuelve lo que hay.
     */
    PagoResponse consultar(Long idReserva, UsuarioAutenticado huesped);

    /**
     * Procesa un aviso (webhook) de la pasarela: verifica su firma y aplica su resultado.
     *
     * @param cuerpo contenido exacto recibido, sin modificar: la firma se calcula sobre él
     * @param firma  encabezado Stripe-Signature
     */
    void procesarEvento(String cuerpo, String firma);
}
