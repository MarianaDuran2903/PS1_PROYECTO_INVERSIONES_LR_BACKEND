package com.inversioneslr.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "Fechas para abrir un periodo disponible de una publicación")
public record AbrirPeriodoDisponibilidadRequest(
        @NotNull(message = "La fecha de inicio es obligatoria")
        @Schema(description = "Primer día disponible, incluido", example = "2026-12-01")
        LocalDate fechaInicio,

        @NotNull(message = "La fecha de fin es obligatoria")
        @Schema(description = "Último día del periodo, excluido", example = "2026-12-10")
        LocalDate fechaFin
) {
    @AssertTrue(message = "La fecha de fin debe ser posterior a la fecha de inicio")
    @Schema(hidden = true)
    public boolean isRangoFechasValido() {
        return fechaInicio == null || fechaFin == null || fechaFin.isAfter(fechaInicio);
    }
}
