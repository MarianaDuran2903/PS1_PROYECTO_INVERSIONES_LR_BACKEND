package com.inversioneslr.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Sesión activa del usuario que hace la petición")
public record SesionResponse(

        @Schema(description = "Usuario de la sesión, con su rol")
        UsuarioResponse usuario,

        @Schema(description = "Fecha y hora en que vence el token (UTC)", example = "2026-10-28T16:00:00Z")
        Instant expiraEn
) {
}
