package com.inversioneslr.backend.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de las reservas.
 */
@Validated
@ConfigurationProperties(prefix = "reservas")
public record ReservaProperties(

        // Minutos que una reserva espera el pago antes de vencer y devolver las fechas al calendario.
        @Positive
        long minutosVigencia
) {
}
