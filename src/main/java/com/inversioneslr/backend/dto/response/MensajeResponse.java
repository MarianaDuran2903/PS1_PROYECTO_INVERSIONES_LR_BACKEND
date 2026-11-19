package com.inversioneslr.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta con un mensaje para mostrar al usuario")
public record MensajeResponse(

        @Schema(description = "Mensaje para el usuario",
                example = "Si el correo está registrado, recibirás las instrucciones para restablecer tu contraseña")
        String mensaje
) {
}
