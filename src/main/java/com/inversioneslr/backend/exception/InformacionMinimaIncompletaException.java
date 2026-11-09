package com.inversioneslr.backend.exception;

import java.util.Map;

/**
 * La publicación no tiene toda la información mínima para solicitar su activación. Lleva todos los campos
 * pendientes, no solo el primero. Se responde 400.
 */
public class InformacionMinimaIncompletaException extends RuntimeException {

    private final Map<String, String> pendientes;

    public InformacionMinimaIncompletaException(Map<String, String> pendientes) {
        super("La publicación no está lista para revisión");
        this.pendientes = Map.copyOf(pendientes);
    }

    public Map<String, String> getPendientes() {
        return pendientes;
    }
}
