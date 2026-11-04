package com.inversioneslr.backend.exception;

/**
 * El Anfitrión pidió una publicación que pertenece a otro Anfitrión. Se responde 403; el intento ya quedó
 * registrado en la auditoría.
 */
public class PublicacionAjenaException extends RuntimeException {

    public PublicacionAjenaException() {
        super("No tiene permisos para esta operación");
    }
}
