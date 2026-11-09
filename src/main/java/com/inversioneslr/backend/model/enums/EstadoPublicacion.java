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

    /**
     * El Administrador interviene en cualquier estado salvo ACTIVA: lo que se ve en las búsquedas se pausa
     * antes de modificarse.
     */
    public boolean permiteIntervencionAdministrativa() {
        return this != ACTIVA;
    }

    /**
     * En revisión o activa, la base exige descripción, dirección y reglas.
     */
    public boolean exigeInformacionCompleta() {
        return this == PENDIENTE_REVISION || this == ACTIVA;
    }

    /**
     * Transiciones permitidas del flujo de revisión. El Anfitrión envía BORRADOR a PENDIENTE_REVISION; el
     * Administrador activa, pausa o bloquea. Tras una pausa, el Administrador revisa y activa o bloquea; una
     * BLOQUEADA no tiene salida definida todavía.
     */
    public boolean puedePasarA(EstadoPublicacion destino) {
        return switch (this) {
            case BORRADOR -> destino == PENDIENTE_REVISION;
            case PENDIENTE_REVISION -> destino == ACTIVA || destino == BLOQUEADA;
            case ACTIVA -> destino == PAUSADA || destino == BLOQUEADA;
            case PAUSADA -> destino == ACTIVA || destino == BLOQUEADA;
            case BLOQUEADA -> false;
        };
    }
}
