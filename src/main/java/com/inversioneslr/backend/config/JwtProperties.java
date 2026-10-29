package com.inversioneslr.backend.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del JWT. El secreto viene de la variable de entorno JWT_SECRET (regla 11).
 */
@Validated
@ConfigurationProperties(prefix = "seguridad.jwt")
public record JwtProperties(

        // HS256 exige una clave de al menos 256 bits (32 bytes).
        @NotBlank(message = "Defina la variable de entorno JWT_SECRET")
        @Size(min = 32, message = "JWT_SECRET debe tener al menos 32 caracteres")
        String secreto,

        @Positive
        long expiracionMinutos
) {
}
