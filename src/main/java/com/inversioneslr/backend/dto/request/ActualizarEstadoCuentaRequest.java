package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Solicitud para cambiar el estado de la cuenta autenticada")
public record ActualizarEstadoCuentaRequest(

        @Schema(
                description = "Nuevo estado de la cuenta. "
                        + "Este endpoint solo permite desactivar; la reactivación se realiza "
                        + "mediante POST /api/v1/auth/reactivar.",
                example = "false")
        @NotNull(message = "El estado de la cuenta es obligatorio")
        Boolean cuentaActiva
) {
}