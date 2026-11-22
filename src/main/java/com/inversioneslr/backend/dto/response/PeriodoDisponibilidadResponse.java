package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.PeriodoDisponibilidad;

import java.time.LocalDate;

public record PeriodoDisponibilidadResponse(
        Long idPeriodo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String tipo
) {
    public static PeriodoDisponibilidadResponse desde(PeriodoDisponibilidad periodo) {
        return new PeriodoDisponibilidadResponse(
                periodo.getIdPeriodo(),
                periodo.getFechaInicio(),
                periodo.getFechaFin(),
                periodo.getTipo().name()
        );
    }
}
