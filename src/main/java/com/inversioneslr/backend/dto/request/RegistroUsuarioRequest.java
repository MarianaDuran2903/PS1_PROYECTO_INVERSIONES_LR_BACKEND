package com.inversioneslr.backend.dto.request;

import com.inversioneslr.backend.validation.ContrasenaSegura;
import com.inversioneslr.backend.validation.ContrasenasCoinciden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos del formulario de registro, comunes a Huésped y Anfitrión. El rol no viaja en el cuerpo:
 * lo fija el servidor según el endpoint.
 */
@Schema(description = "Datos para crear una cuenta de Huésped o Anfitrión")
@ContrasenasCoinciden
public record RegistroUsuarioRequest(

        @Schema(description = "Primer nombre", example = "Carlos", maxLength = 50)
        @NotBlank(message = "El primer nombre es obligatorio")
        @Size(max = 50, message = "El primer nombre admite máximo 50 caracteres")
        @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE)
        String primerNombre,

        @Schema(description = "Segundo nombre (opcional)", example = "Andrés", maxLength = 50,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "El segundo nombre admite máximo 50 caracteres")
        @Pattern(regexp = NOMBRE_OPCIONAL, message = MENSAJE_NOMBRE)
        String segundoNombre,

        @Schema(description = "Primer apellido", example = "Gómez", maxLength = 50)
        @NotBlank(message = "El primer apellido es obligatorio")
        @Size(max = 50, message = "El primer apellido admite máximo 50 caracteres")
        @Pattern(regexp = NOMBRE, message = MENSAJE_NOMBRE)
        String primerApellido,

        @Schema(description = "Segundo apellido (opcional)", example = "O'Neill", maxLength = 50,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 50, message = "El segundo apellido admite máximo 50 caracteres")
        @Pattern(regexp = NOMBRE_OPCIONAL, message = MENSAJE_NOMBRE)
        String segundoApellido,

        @Schema(description = "Correo electrónico (único, sin distinguir mayúsculas)",
                example = "carlos.gomez@correo.com", maxLength = 254)
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres")
        String correo,

        @Schema(description = "Contraseña de 8 a 64 caracteres con mayúscula, minúscula, número y "
                + "carácter especial, sin espacios", example = "Segura#123",
                minLength = 8, maxLength = 64, accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 64, message = "La contraseña debe tener entre 8 y 64 caracteres")
        @ContrasenaSegura
        String contrasena,

        @Schema(description = "Debe ser igual a la contraseña", example = "Segura#123",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La confirmación de la contraseña es obligatoria")
        String confirmarContrasena
) {

    /** Letras (con tildes y ñ) separadas por un espacio, guion o apóstrofe. Admite espacios al inicio o al final. */
    static final String NOMBRE = "^\\s*\\p{L}+(?:[ '-]\\p{L}+)*\\s*$";
    /** Igual que NOMBRE, pero también acepta vacío o solo espacios (el campo no se escribió). */
    static final String NOMBRE_OPCIONAL = "^\\s*(?:\\p{L}+(?:[ '-]\\p{L}+)*)?\\s*$";
    static final String MENSAJE_NOMBRE = "Solo se permiten letras, espacios, guion o apóstrofe";

    // Evita que las contraseñas aparezcan en logs o trazas si el objeto se imprime.
    @Override
    public String toString() {
        return "RegistroUsuarioRequest[primerNombre=" + primerNombre + ", segundoNombre=" + segundoNombre
                + ", primerApellido=" + primerApellido + ", segundoApellido=" + segundoApellido
                + ", correo=" + correo + ", contrasena=****, confirmarContrasena=****]";
    }
}
