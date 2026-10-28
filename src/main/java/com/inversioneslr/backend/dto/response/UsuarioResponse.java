package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos públicos de un usuario (nunca incluye la contraseña)")
public record UsuarioResponse(

        @Schema(description = "Identificador del usuario", example = "1")
        Long idUsuario,

        @Schema(description = "Primer nombre", example = "Carlos")
        String primerNombre,

        @Schema(description = "Segundo nombre (nulo si no se registró)", example = "Andrés", nullable = true)
        String segundoNombre,

        @Schema(description = "Primer apellido", example = "Gómez")
        String primerApellido,

        @Schema(description = "Segundo apellido (nulo si no se registró)", example = "O'Neill", nullable = true)
        String segundoApellido,

        @Schema(description = "Correo electrónico", example = "carlos.gomez@correo.com")
        String correo,

        @Schema(description = "Rol del usuario", example = "ANFITRION")
        Rol rol,

        @Schema(description = "Indica si la cuenta está activa", example = "true")
        boolean cuentaActiva
) {
}
