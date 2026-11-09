package com.inversioneslr.backend.exception;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;

/**
 * El estado actual de la publicación no permite la transición pedida. Se responde 409.
 */
public class TransicionNoPermitidaException extends RuntimeException {

    private TransicionNoPermitidaException(String mensaje) {
        super(mensaje);
    }

    /**
     * Solo una publicación en BORRADOR puede enviarse a revisión.
     */
    public static TransicionNoPermitidaException alEnviarARevision(EstadoPublicacion actual) {
        return new TransicionNoPermitidaException(switch (actual) {
            case PENDIENTE_REVISION -> "La publicación ya está en revisión";
            case ACTIVA -> "La publicación ya está activa";
            case PAUSADA -> "La publicación está pausada: el Administrador gestiona su revisión";
            case BLOQUEADA -> "La publicación está bloqueada y no se puede enviar a revisión";
            case BORRADOR -> "La publicación no se puede enviar a revisión en su estado actual";
        });
    }
}
