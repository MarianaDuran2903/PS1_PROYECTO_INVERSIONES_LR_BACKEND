package com.inversioneslr.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Datos del Administrador que se carga como dato inicial (variables ADMIN_CORREO y ADMIN_CONTRASENA).
 * Nunca se guardan en el código ni en el repositorio.
 */
@ConfigurationProperties(prefix = "seguridad.admin-inicial")
public record AdminInicialProperties(String correo, String contrasena) {

    public boolean configurado() {
        return correo != null && !correo.isBlank() && contrasena != null && !contrasena.isBlank();
    }
}
