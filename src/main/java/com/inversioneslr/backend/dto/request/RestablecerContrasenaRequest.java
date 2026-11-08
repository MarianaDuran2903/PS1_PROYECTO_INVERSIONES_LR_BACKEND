package com.inversioneslr.backend.dto.request;

import com.inversioneslr.backend.validation.ConfirmacionDeContrasena;
import com.inversioneslr.backend.validation.ContrasenaSegura;
import com.inversioneslr.backend.validation.ContrasenasCoinciden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Token de recuperación y nueva contraseña. La contraseña sigue las mismas reglas que en el registro.
 */
@Schema(description = "Datos para establecer una nueva contraseña con el token de recuperación")
@ContrasenasCoinciden
public record RestablecerContrasenaRequest(

        @Schema(description = "Token recibido en el correo simulado de recuperación",
                example = "q3Jx9Vf0bT2yWn8cLk4sHd7pRz1aEm6uGi5oYt0wXjA", maxLength = 100)
        @NotBlank(message = "El token de recuperación es obligatorio")
        @Size(max = 100, message = "El token de recuperación no es válido")
        String token,

        @Schema(description = "Nueva contraseña de 8 a 64 caracteres con mayúscula, minúscula, número y "
                + "carácter especial, sin espacios", example = "Nueva#456",
                minLength = 8, maxLength = 64, accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
        @ContrasenaSegura
        String contrasena,

        @Schema(description = "Debe ser igual a la nueva contraseña", example = "Nueva#456",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La confirmación de la contraseña es obligatoria")
        String confirmarContrasena
) implements ConfirmacionDeContrasena {

    // Evita que el token y las contraseñas aparezcan en logs o trazas si el objeto se imprime.
    @Override
    public String toString() {
        return "RestablecerContrasenaRequest[token=****, contrasena=****, confirmarContrasena=****]";
    }
}
