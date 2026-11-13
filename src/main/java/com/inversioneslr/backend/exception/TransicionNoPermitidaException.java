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

    /**
     * Solo una publicación en PENDIENTE_REVISION puede aprobarse o rechazarse.
     */
    public static TransicionNoPermitidaException alRevisar(EstadoPublicacion actual) {
        return new TransicionNoPermitidaException(switch (actual) {
            case BORRADOR -> "La publicación está en borrador: el Anfitrión aún no la envía a revisión";
            case ACTIVA -> "La publicación ya está activa";
            case PAUSADA -> "La publicación está pausada y no está pendiente de revisión";
            case BLOQUEADA -> "La publicación está bloqueada y no está pendiente de revisión";
            case PENDIENTE_REVISION -> "La publicación no se puede revisar en su estado actual";
        });
    }

    /**
     * Solo una publicación ACTIVA puede pausarse.
     */
    public static TransicionNoPermitidaException alPausar(EstadoPublicacion actual) {
        return new TransicionNoPermitidaException(switch (actual) {
            case BORRADOR -> "La publicación está en borrador: aún no se ha publicado, no hay nada que pausar";
            case PENDIENTE_REVISION -> "La publicación está en revisión: solo se puede pausar una publicación activa";
            case PAUSADA -> "La publicación ya está pausada";
            case BLOQUEADA -> "La publicación está bloqueada y no se puede pausar";
            case ACTIVA -> "La publicación no se puede pausar en su estado actual";
        });
    }

    /**
     * El Administrador bloquea una publicación en revisión, activa o pausada; un borrador aún no se ha
     * publicado y una bloqueada ya lo está.
     */
    public static TransicionNoPermitidaException alBloquear(EstadoPublicacion actual) {
        return new TransicionNoPermitidaException(switch (actual) {
            case BORRADOR -> "La publicación está en borrador: aún no se ha publicado, no hay nada que bloquear";
            case BLOQUEADA -> "La publicación ya está bloqueada";
            case PENDIENTE_REVISION, ACTIVA, PAUSADA -> "La publicación no se puede bloquear en su estado actual";
        });
    }

    /**
     * El Administrador reactiva solo una publicación pausada o bloqueada; una en revisión se aprueba.
     */
    public static TransicionNoPermitidaException alReactivar(EstadoPublicacion actual) {
        return new TransicionNoPermitidaException(switch (actual) {
            case ACTIVA -> "La publicación ya está activa";
            case BORRADOR -> "La publicación está en borrador: el Anfitrión debe enviarla a revisión";
            case PENDIENTE_REVISION -> "La publicación está en revisión: se aprueba, no se reactiva";
            case PAUSADA, BLOQUEADA -> "La publicación no se puede reactivar en su estado actual";
        });
    }
}
