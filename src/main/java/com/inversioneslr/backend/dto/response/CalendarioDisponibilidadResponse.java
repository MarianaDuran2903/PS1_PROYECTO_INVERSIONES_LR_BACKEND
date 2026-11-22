package com.inversioneslr.backend.dto.response;

import java.util.List;

public record CalendarioDisponibilidadResponse(
        Long idPublicacion,
        String titulo,
        List<PeriodoDisponibilidadResponse> periodos
) {
}