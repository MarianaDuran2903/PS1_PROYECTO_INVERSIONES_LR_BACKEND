package com.inversioneslr.backend.exception;

/**
 * La reserva no admite iniciar un pago: ya está confirmada, venció, se canceló o agotó sus intentos. Se
 * responde 409 con el motivo.
 */
public class PagoNoPermitidoException extends RuntimeException {

    public PagoNoPermitidoException(String mensaje) {
        super(mensaje);
    }
}
