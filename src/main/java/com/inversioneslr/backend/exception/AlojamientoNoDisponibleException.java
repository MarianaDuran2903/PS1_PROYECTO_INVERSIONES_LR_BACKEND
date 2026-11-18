package com.inversioneslr.backend.exception;

public class AlojamientoNoDisponibleException extends RuntimeException {
    public AlojamientoNoDisponibleException() {
        super("El alojamiento no está disponible");
    }
}
