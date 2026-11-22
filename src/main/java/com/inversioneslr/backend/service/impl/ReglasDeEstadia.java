package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.exception.DatosReservaInvalidosException;
import com.inversioneslr.backend.model.Publicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Reglas que una estadía debe cumplir tanto para cotizarla como para reservarla, de modo que las dos operaciones
 * rechacen lo mismo y con los mismos mensajes.
 */
final class ReglasDeEstadia {

    /** "Hoy" se cuenta en la zona del negocio: a las 8 p. m. en Colombia ya es el día siguiente en UTC. */
    static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Bogota");

    private ReglasDeEstadia() {
    }

    static void exigirLlegadaDesdeHoy(Clock reloj, LocalDate llegada) {
        LocalDate hoy = LocalDate.now(reloj.withZone(ZONA_NEGOCIO));
        if (llegada.isBefore(hoy)) {
            throw new DatosReservaInvalidosException("fechaLlegada", "La fecha de llegada no puede ser anterior a hoy");
        }
    }

    static void exigirCapacidad(Publicacion publicacion, int huespedes) {
        if (huespedes > publicacion.getCapacidad()) {
            throw new DatosReservaInvalidosException("huespedes",
                    "El alojamiento admite como máximo " + publicacion.getCapacidad() + " huéspedes");
        }
    }
}
