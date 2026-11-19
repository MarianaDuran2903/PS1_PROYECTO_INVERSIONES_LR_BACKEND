package com.inversioneslr.backend.exception;

/**
 * Un dato del cuerpo apunta a un registro que no existe (por ejemplo, una ciudad o un tipo de alojamiento).
 * Se responde 400 con el error en el campo correspondiente.
 */
public class ReferenciaInvalidaException extends RuntimeException {

    private final String campo;

    public ReferenciaInvalidaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
