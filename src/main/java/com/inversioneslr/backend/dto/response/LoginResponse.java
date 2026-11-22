package com.inversioneslr.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sesión creada: token para el encabezado Authorization y datos del usuario")
public record LoginResponse(

        @Schema(description = "JWT que se envía como 'Authorization: Bearer <token>'",
                example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI1In0.firma")
        String token,

        @Schema(description = "Tipo de token", example = "Bearer")
        String tipo,

        @Schema(description = "Segundos que faltan para que el token venza", example = "3600")
        long expiraEn,

        @Schema(description = "Usuario autenticado, con su rol")
        UsuarioResponse usuario
) {

    public static final String TIPO_BEARER = "Bearer";
}
