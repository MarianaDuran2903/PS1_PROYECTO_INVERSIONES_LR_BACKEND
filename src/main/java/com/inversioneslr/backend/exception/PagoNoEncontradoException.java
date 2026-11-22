package com.inversioneslr.backend.exception;

/**
 * La reserva todavía no tiene ningún pago iniciado. Se responde 404.
 */
public class PagoNoEncontradoException extends RuntimeException {

    public PagoNoEncontradoException() {
        super("La reserva no tiene un pago iniciado");
    }
}
