package com.inversioneslr.backend.exception;

/**
 * La operación solicitada no es válida para el estado actual de la cuenta.
 */
public class EstadoCuentaInvalidoException extends RuntimeException {
    public EstadoCuentaInvalidoException(String message) {
        super(message);
    }
}
