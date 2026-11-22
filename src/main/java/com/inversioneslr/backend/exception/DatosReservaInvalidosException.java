package com.inversioneslr.backend.exception;

/**
 * Un dato de la solicitud de reserva o de cotización no cumple una regla del negocio (por ejemplo, una llegada en
 * el pasado, más huéspedes que la capacidad o un servicio adicional que la publicación no ofrece). Se responde 400
 * con el error en el campo correspondiente.
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
