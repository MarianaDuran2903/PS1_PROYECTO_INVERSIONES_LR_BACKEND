package com.inversioneslr.backend.model.enums;

/**
 * Estado de una reserva (columna estado de la tabla reserva).
 */
public enum EstadoReserva {
    /** Creada y a la espera del pago; ocupa las fechas hasta que se paga o vence. */
    PENDIENTE_PAGO,
    CONFIRMADA,
    /** Vencida sin pago o cancelada; ya no ocupa fechas. */
    CANCELADA,
    REVISION_MANUAL;

    /**
     * Si una reserva en este estado impide que otro Huésped tome las mismas noches.
     */
    public boolean ocupaFechas() {
        return this != CANCELADA;
    }
}
