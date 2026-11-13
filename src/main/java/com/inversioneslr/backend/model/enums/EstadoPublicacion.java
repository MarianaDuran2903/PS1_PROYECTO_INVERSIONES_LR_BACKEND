package com.inversioneslr.backend.model.enums;

/**
 * Estados de una publicación (columna publicacion.estado). Flujo: el Anfitrión crea en BORRADOR y envía a
 * PENDIENTE_REVISION; el Administrador activa o bloquea; el Anfitrión pausa una ACTIVA. Solo las ACTIVAS se
 * ofrecen en las búsquedas: una BLOQUEADA, como una PAUSADA, no se presenta para nuevas reservas.
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
     * El Administrador reactiva una PAUSADA tras revisar el ajuste del Anfitrión, o una BLOQUEADA según el
     * motivo del bloqueo.
     */
    public boolean permiteReactivacion() {
        return this == PAUSADA || this == BLOQUEADA;
    }

    /**
     * En revisión o activa, la base exige descripción, dirección y reglas.
     */
    public boolean exigeInformacionCompleta() {
        return this == PENDIENTE_REVISION || this == ACTIVA;
    }

    /**
     * Transiciones permitidas del flujo de revisión. El Anfitrión envía BORRADOR a PENDIENTE_REVISION. El
     * Administrador revisa una PENDIENTE_REVISION: la activa, la devuelve a BORRADOR para que el Anfitrión la
     * corrija y la reenvíe, o la bloquea. A una ACTIVA la pausa su Anfitrión o la bloquea el Administrador. Tras
     * una pausa, el Administrador revisa y activa o bloquea. Un BORRADOR no se bloquea porque aún no está
     * publicado. Una BLOQUEADA vuelve a ACTIVA si el Administrador lo decide según el motivo del bloqueo.
     */
    public boolean puedePasarA(EstadoPublicacion destino) {
        return switch (this) {
            case BORRADOR -> destino == PENDIENTE_REVISION;
            case PENDIENTE_REVISION -> destino == ACTIVA || destino == BORRADOR || destino == BLOQUEADA;
            case ACTIVA -> destino == PAUSADA || destino == BLOQUEADA;
            case PAUSADA -> destino == ACTIVA || destino == BLOQUEADA;
            case BLOQUEADA -> destino == ACTIVA;
        };
    }
}
