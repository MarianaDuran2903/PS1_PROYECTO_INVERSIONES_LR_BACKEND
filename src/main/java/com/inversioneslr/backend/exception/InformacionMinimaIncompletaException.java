package com.inversioneslr.backend.exception;

import java.util.Map;

/**
 * La publicación no tiene toda la información mínima para solicitar su activación o para activarse. Lleva
 * todos los campos pendientes, no solo el primero. Se responde 400.
 */
public class InformacionMinimaIncompletaException extends RuntimeException {

    private final Map<String, String> pendientes;

    public InformacionMinimaIncompletaException(Map<String, String> pendientes) {
        this("La publicación no está lista para revisión", pendientes);
    }

    public InformacionMinimaIncompletaException(String mensaje, Map<String, String> pendientes) {
        super(mensaje);
        this.pendientes = Map.copyOf(pendientes);
    }

    public Map<String, String> getPendientes() {
        return pendientes;
    }
}
