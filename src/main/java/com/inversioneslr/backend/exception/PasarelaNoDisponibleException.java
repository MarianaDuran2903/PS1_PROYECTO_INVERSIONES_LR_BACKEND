package com.inversioneslr.backend.exception;

/**
 * No se pudo hablar con la pasarela de pagos, o los pagos no están configurados. Se responde 502. El detalle
 * técnico queda en el log; el mensaje no lo revela.
 */
public class PasarelaNoDisponibleException extends RuntimeException {

    public PasarelaNoDisponibleException() {
        super("No se pudo comunicar con la pasarela de pagos. Intente de nuevo en unos minutos");
    }

    public PasarelaNoDisponibleException(Throwable causa) {
        super("No se pudo comunicar con la pasarela de pagos. Intente de nuevo en unos minutos", causa);
    }
}
