package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reactivación de una publicación pausada o bloqueada: el motivo explica por qué el Administrador la vuelve a
 * ofrecer.
 */
@Schema(description = "Reactivación de una publicación pausada o bloqueada con el motivo de la decisión")
public record ReactivacionPublicacionRequest(

        @Schema(description = "Motivo de la reactivación; queda registrado en la auditoría",
                example = "El Anfitrión corrigió las fotos y la dirección, y se verificaron", minLength = 10,
                maxLength = 500)
        @NotBlank(message = "El motivo de la reactivación es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo
) {
}
