package com.inversioneslr.backend.exception;

/**
 * La reserva pedida no existe. Se responde 404.
 */
public class ReservaNoEncontradaException extends RuntimeException {

    public ReservaNoEncontradaException() {
        super("La reserva no existe");
    }
}
