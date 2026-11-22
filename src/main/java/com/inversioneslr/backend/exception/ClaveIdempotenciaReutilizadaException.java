package com.inversioneslr.backend.exception;

/**
 * La clave de idempotencia ya se usó para una reserva distinta de la que se pide ahora. Una clave identifica una
 * sola solicitud: para reservar otra cosa hay que enviar otra clave. Se responde 409.
 */
public class ClaveIdempotenciaReutilizadaException extends RuntimeException {

    public ClaveIdempotenciaReutilizadaException() {
        super("La clave de idempotencia ya se usó con una solicitud distinta");
    }
}
