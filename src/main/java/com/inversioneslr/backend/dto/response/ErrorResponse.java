package com.inversioneslr.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Map;

@Schema(description = "Formato estándar de error de la API")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(description = "Fecha y hora del error", example = "2026-10-27T15:00:00Z")
        OffsetDateTime fecha,

        @Schema(description = "Código HTTP", example = "400")
        int estado,

        @Schema(description = "Nombre del código HTTP", example = "Bad Request")
        String error,

        @Schema(description = "Mensaje para el usuario", example = "Los datos enviados no son válidos")
        String mensaje,

        @Schema(description = "Ruta solicitada", example = "/api/v1/auth/registro/huesped")
        String ruta,

        @Schema(description = "Errores por campo (solo en errores de validación)",
                example = "{\"correo\": \"El correo no tiene un formato válido\"}")
        Map<String, String> errores
) {
}
