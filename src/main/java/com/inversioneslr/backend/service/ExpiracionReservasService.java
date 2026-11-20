package com.inversioneslr.backend.service;

/**
 * Vencimiento de las reservas que no se pagaron a tiempo: pasan a CANCELADA y devuelven sus fechas al calendario.
 */
public interface ExpiracionReservasService {

    /**
     * Vence, en un lote acotado, las reservas pendientes de pago cuyo plazo ya terminó. Lo ejecuta la tarea
     * programada; si quedan más que el tamaño del lote, la siguiente ejecución las toma.
     *
     * @return cuántas reservas venció
     */
    int vencerPendientes();

    /**
     * Igual que {@link #vencerPendientes()}, pero solo de una publicación y con los cambios ya escritos al
     * terminar, para que quien la llama pueda reservar esas mismas fechas en su transacción.
     *
     * @return cuántas reservas venció
     */
    int vencerPendientesDe(Long idPublicacion);
}
