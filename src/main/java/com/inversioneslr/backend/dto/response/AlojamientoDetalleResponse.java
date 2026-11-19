package com.inversioneslr.backend.dto.response;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Detalle público de un alojamiento activo")
public record AlojamientoDetalleResponse(
        Long idPublicacion,
        String titulo,
        String descripcion,
        EstadoPublicacion estado,
        CatalogoItemResponse ciudad,
        String barrioZona,
        CatalogoItemResponse tipo,
        Integer capacidad,
        BigDecimal precioNoche,
        String reglas,
        PoliticaResponse politica,
        List<CatalogoItemResponse> servicios,
        List<ImagenResponse> imagenes
) {

    public record PoliticaResponse(
            Long id,
            String nombre,
            Integer horasAnticipacionMin,
            BigDecimal porcentajeDevolucion
    ) {
    }

    @Schema(description = "Ruta o referencia de imagen configurada para la publicación")
    public record ImagenResponse(
            Integer orden,
            String ruta
    ) {
    }
}