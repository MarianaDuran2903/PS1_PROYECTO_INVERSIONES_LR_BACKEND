package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.CotizarEstanciaRequest;
import com.inversioneslr.backend.dto.response.CotizacionResponse;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.service.CotizacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cotizaciones")
@Tag(name = "Cotizaciones", description = "Cotización de estadías antes de reservar")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HUESPED')")
    @Operation(summary = "Cotizar una estadía",
            description = "Calcula lo que costaría hospedarse en una publicación activa entre dos fechas, sin reservar "
                    + "ni bloquear nada: noches, precio base, descuento por estadía larga, tarifa de limpieza, "
                    + "servicios adicionales elegidos, tarifa de servicio y total. Es el mismo cálculo que usa la "
                    + "reserva. Si las fechas son inválidas o alguna noche no está disponible no se genera la "
                    + "cotización. Solo rol HUESPED.")
    @ApiResponse(responseCode = "200", description = "Cotización calculada",
            content = @Content(schema = @Schema(implementation = CotizacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos, llegada anterior a hoy, huéspedes por encima de la capacidad o servicio "
                    + "adicional que la publicación no ofrece",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Huésped",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El alojamiento no existe o no está disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Alguna noche del rango no está disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public CotizacionResponse cotizar(@Valid @RequestBody CotizarEstanciaRequest request) {
        return cotizacionService.cotizar(request);
    }
}
