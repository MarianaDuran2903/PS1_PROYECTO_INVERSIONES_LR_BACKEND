package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Resumen público de un alojamiento activo")
public record AlojamientoResumenResponse(
        Long idPublicacion,
        String titulo,
        EstadoPublicacion estado,
        CatalogoItemResponse ciudad,
        CatalogoItemResponse tipo,
        Integer capacidad,
        BigDecimal precioNoche,
        List<CatalogoItemResponse> servicios
) {

    public AlojamientoResumenResponse {
        servicios = List.copyOf(servicios);
    }
}