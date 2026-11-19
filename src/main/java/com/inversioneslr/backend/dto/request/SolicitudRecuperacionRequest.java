package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Correo de la cuenta cuya contraseña se quiere recuperar")
public record SolicitudRecuperacionRequest(

        @Schema(description = "Correo con el que se registró la cuenta", example = "carlos.gomez@correo.com",
                maxLength = 254)
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres")
        String correo
) {
}
