package com.inversioneslr.backend.exception;

/**
 * No se puede cotizar la estadía porque alguna de sus noches no está disponible: no hay calendario para ella, está
 * bloqueada o una reserva ya la ocupa. Se responde 409.
 */
public class CotizacionNoDisponibleException extends RuntimeException {

    public CotizacionNoDisponibleException() {
        super("No se puede generar una cotización para las fechas seleccionadas");
    }
}
