package com.inversioneslr.backend.exception;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;

/**
 * El estado de la publicación no permite que el Anfitrión la edite. Se responde 409.
 */
public class EstadoNoEditableException extends RuntimeException {

    public EstadoNoEditableException(EstadoPublicacion estado) {
        super(mensaje(estado));
    }

    private static String mensaje(EstadoPublicacion estado) {
        return switch (estado) {
            case ACTIVA -> "La publicación está activa: solicite pausarla para editarla";
            case PENDIENTE_REVISION -> "La publicación está en revisión y no se puede editar";
            case BLOQUEADA -> "La publicación está bloqueada y no se puede editar";
            case BORRADOR, PAUSADA -> "La publicación no se puede editar en su estado actual";
        };
    }
}
