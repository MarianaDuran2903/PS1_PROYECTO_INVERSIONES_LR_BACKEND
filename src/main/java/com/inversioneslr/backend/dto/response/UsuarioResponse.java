package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Datos públicos de un usuario (nunca incluye la contraseña)")
public record UsuarioResponse(

        @Schema(description = "Identificador del usuario", example = "1")
        Long idUsuario,

        @Schema(description = "Nombre completo", example = "Ana Pérez")
        String nombre,

        @Schema(description = "Correo electrónico", example = "ana.perez@correo.com")
        String correo,

        @Schema(description = "Rol del usuario", example = "HUESPED")
        Rol rol,

        @Schema(description = "Indica si la cuenta está activa", example = "true")
        boolean cuentaActiva
) {
}
