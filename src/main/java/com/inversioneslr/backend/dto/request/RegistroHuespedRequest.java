package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear una cuenta con rol Huésped")
public record RegistroHuespedRequest(

        @Schema(description = "Nombre completo", example = "Ana Pérez", maxLength = 120)
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120, message = "El nombre admite máximo 120 caracteres")
        String nombre,

        @Schema(description = "Correo electrónico (único, sin distinguir mayúsculas)",
                example = "ana.perez@correo.com", maxLength = 254)
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 254, message = "El correo admite máximo 254 caracteres")
        String correo,

        @Schema(description = "Contraseña de 8 a 12 caracteres", example = "Segura123",
                minLength = 8, maxLength = 12, accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 12, message = "La contraseña debe tener entre 8 y 12 caracteres")
        String contrasena
) {

    // Evita que la contraseña aparezca en logs o trazas si el objeto se imprime.
    @Override
    public String toString() {
        return "RegistroHuespedRequest[nombre=" + nombre + ", correo=" + correo + ", contrasena=****]";
    }
}
