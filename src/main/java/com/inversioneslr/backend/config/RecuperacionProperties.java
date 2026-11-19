package com.inversioneslr.backend.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la recuperación de contraseña simulada.
 */
@Validated
@ConfigurationProperties(prefix = "seguridad.recuperacion")
public record RecuperacionProperties(

        // Minutos que el enlace de recuperación es válido desde que se solicita.
        @Positive
        long minutosValidez
) {
}
