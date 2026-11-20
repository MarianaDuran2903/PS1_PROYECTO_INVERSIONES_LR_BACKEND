package com.inversioneslr.backend.exception;

/**
 * Un dato de la solicitud de reserva no cumple una regla del negocio (por ejemplo, una llegada en el pasado o más
 * huéspedes que la capacidad). Se responde 400 con el error en el campo correspondiente.
 */
public class DatosReservaInvalidosException extends RuntimeException {

    private final String campo;

    public DatosReservaInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
