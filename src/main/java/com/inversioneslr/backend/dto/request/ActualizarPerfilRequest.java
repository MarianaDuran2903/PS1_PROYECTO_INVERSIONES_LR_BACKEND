package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos editables del perfil del usuario autenticado")
public record ActualizarPerfilRequest(

        @Schema(description = "Primer nombre", example = "Carlos")
        @NotBlank(message = "El primer nombre es obligatorio")
        @Size(max = 50, message = "El primer nombre no puede superar 50 caracteres")
        @Pattern(regexp = "^ *(?:\\p{L}[\\p{L}\\p{M}]*(?: +\\p{L}[\\p{L}\\p{M}]*)*)? *$", message = "Solo se admiten letras y espacios")
        String primerNombre,

        @Schema(description = "Segundo nombre", example = "Andrés", nullable = true)
        @Size(max = 50, message = "El segundo nombre no puede superar 50 caracteres")
        @Pattern(regexp = "^ *(?:\\p{L}[\\p{L}\\p{M}]*(?: +\\p{L}[\\p{L}\\p{M}]*)*)? *$", message = "Solo se admiten letras y espacios")
        String segundoNombre,

        @Schema(description = "Primer apellido", example = "Gómez")
        @NotBlank(message = "El primer apellido es obligatorio")
        @Size(max = 50, message = "El primer apellido no puede superar 50 caracteres")
        @Pattern(regexp = "^ *(?:\\p{L}[\\p{L}\\p{M}]*(?: +\\p{L}[\\p{L}\\p{M}]*)*)? *$", message = "Solo se admiten letras y espacios")
        String primerApellido,

        @Schema(description = "Segundo apellido", example = "Pérez", nullable = true)
        @Size(max = 50, message = "El segundo apellido no puede superar 50 caracteres")
        @Pattern(regexp = "^ *(?:\\p{L}[\\p{L}\\p{M}]*(?: +\\p{L}[\\p{L}\\p{M}]*)*)? *$", message = "Solo se admiten letras y espacios")
        String segundoApellido
) {}