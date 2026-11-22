package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Rechazo de una publicación pendiente de revisión: el motivo explica qué debe corregir el Anfitrión.
 */
@Schema(description = "Rechazo de una publicación pendiente con el motivo de la decisión")
public record RechazoPublicacionRequest(

        @Schema(description = "Motivo del rechazo; queda registrado en la auditoría",
                example = "Las fotos y la dirección no coinciden con la descripción", minLength = 10,
                maxLength = 500)
        @NotBlank(message = "El motivo del rechazo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo
) {
}
