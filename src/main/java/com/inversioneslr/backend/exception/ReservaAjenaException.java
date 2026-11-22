package com.inversioneslr.backend.exception;

/**
 * Un Huésped pidió operar sobre la reserva de otro. Se responde 403; el intento ya quedó registrado en la
 * auditoría.
 */
public class ReservaAjenaException extends RuntimeException {

    public ReservaAjenaException() {
        super("No tiene permisos sobre esta reserva");
    }
}
