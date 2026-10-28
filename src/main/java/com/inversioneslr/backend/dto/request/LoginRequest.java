package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credenciales de inicio de sesión. No se aplica la regla de contraseña segura: si no cumple,
 * simplemente no coincide y se responde con el mensaje genérico.
 */
@Schema(description = "Credenciales para iniciar sesión")
public record LoginRequest(

        @Schema(description = "Correo electrónico", example = "carlos.gomez@correo.com")
        @NotBlank(message = "El correo es obligatorio")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres")
        String correo,

        @Schema(description = "Contraseña", example = "Segura#123", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 72, message = "La contraseña admite máximo 72 caracteres")
        String contrasena
) {

    // Evita que la contraseña aparezca en logs o trazas si el objeto se imprime.
    @Override
    public String toString() {
        return "LoginRequest[correo=" + correo + ", contrasena=****]";
    }
}
