package com.inversioneslr.backend.exception;

/**
 * Un cambio dejaría incompleta una publicación que debe tener toda su información según su estado.
 * Se responde 400 con el error en el campo correspondiente.
 */
public class InformacionIncompletaException extends RuntimeException {

    private final String campo;

    public InformacionIncompletaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
