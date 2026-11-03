package com.inversioneslr.backend.model.enums;

/**
 * Estados de una publicación (columna publicacion.estado). Flujo: el Anfitrión crea en BORRADOR y envía a
 * PENDIENTE_REVISION; el Administrador activa, pausa o bloquea.
 */
public enum EstadoPublicacion {
    BORRADOR,
    PENDIENTE_REVISION,
    ACTIVA,
    PAUSADA,
    BLOQUEADA;

    /**
     * El Anfitrión solo edita mientras la publicación no está publicada ni en manos del Administrador:
     * una ACTIVA debe pausarse primero.
     */
    public boolean permiteEdicionDelAnfitrion() {
        return this == BORRADOR || this == PAUSADA;
    }
}
