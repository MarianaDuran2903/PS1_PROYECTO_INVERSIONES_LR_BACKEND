package com.inversioneslr.backend.service;

/**
 * Aplica el resultado de un pago a la reserva. Es el único lugar donde un pago se aprueba, se rechaza o expira, y
 * donde una reserva se confirma por haber sido pagada: el aviso de la pasarela y la consulta directa a ella
 * llegan aquí, así las dos vías se comportan igual.
 */
public interface ResultadoPagoService {

    /**
     * Aplica un aviso de la pasarela ya verificado. Un aviso repetido, de una sesión desconocida o que no
     * interesa no tiene efecto.
     */
    void aplicarEvento(PasarelaPago.EventoPasarela evento);

    /**
     * Aplica lo que la pasarela informa hoy de la sesión de un pago de la reserva. Una sesión todavía abierta no
     * cambia nada.
     */
    void sincronizar(Long idReserva, PasarelaPago.EstadoSesion estado);
}
