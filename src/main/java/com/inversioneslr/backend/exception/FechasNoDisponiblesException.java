package com.inversioneslr.backend.exception;

/**
 * Alguna de las noches pedidas no está disponible: no hay calendario para ella o otra reserva ya la ocupa. Se
 * responde 409.
 */
public class FechasNoDisponiblesException extends RuntimeException {

    public FechasNoDisponiblesException() {
        super("Las fechas seleccionadas no están disponibles");
    }
}
