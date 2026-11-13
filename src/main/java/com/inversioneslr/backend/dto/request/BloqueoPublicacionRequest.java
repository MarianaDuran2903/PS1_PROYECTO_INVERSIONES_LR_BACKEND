package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Bloqueo de una publicación engañosa o incumplidora: el motivo deja constancia de por qué intervino el
 * Administrador.
 */
@Schema(description = "Bloqueo de una publicación con el motivo de la decisión")
public record BloqueoPublicacionRequest(

        @Schema(description = "Motivo del bloqueo; se guarda en la publicación y queda registrado en la auditoría",
                example = "Las fotos pertenecen a otro alojamiento y la dirección no existe", minLength = 10,
                maxLength = 500)
        @NotBlank(message = "El motivo del bloqueo es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo
) {
}
