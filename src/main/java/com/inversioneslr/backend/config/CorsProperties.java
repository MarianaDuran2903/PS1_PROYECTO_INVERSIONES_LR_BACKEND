package com.inversioneslr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Orígenes del frontend que pueden llamar a la API desde el navegador (variable FRONTEND_URL,
 * separada por comas si hay varios).
 */
@ConfigurationProperties(prefix = "seguridad.cors")
public record CorsProperties(List<String> origenesPermitidos) {
}
