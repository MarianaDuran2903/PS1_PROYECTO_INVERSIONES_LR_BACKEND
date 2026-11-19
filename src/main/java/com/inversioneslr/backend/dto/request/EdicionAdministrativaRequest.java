package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Intervención de un Administrador sobre una publicación: los nuevos datos más el motivo que la autoriza.
 * Los datos siguen las mismas reglas que la edición del Anfitrión.
 */
@Schema(description = "Edición administrativa de una publicación con el motivo de la intervención")
public record EdicionAdministrativaRequest(

        @Schema(description = "Motivo de la intervención; queda registrado en la auditoría",
                example = "Corrección de la dirección reportada por el Anfitrión", minLength = 10, maxLength = 500)
        @NotBlank(message = "El motivo de la intervención es obligatorio")
        @Size(min = 10, max = 500, message = "El motivo debe tener entre 10 y 500 caracteres")
        String motivo,

        @Schema(description = "Nueva información de la publicación")
        @NotNull(message = "Los cambios de la publicación son obligatorios")
        @Valid
        ActualizarPublicacionRequest cambios
) {
}
