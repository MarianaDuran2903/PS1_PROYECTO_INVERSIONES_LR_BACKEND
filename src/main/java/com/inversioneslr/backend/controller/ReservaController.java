package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.CrearReservaRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.ReservaResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.ReservaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas")
@Tag(name = "Reservas", description = "Reservas de alojamientos por el Huésped")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class ReservaController {

    /** Longitud máxima de la clave: es la de la columna clave_idempotencia. */
    static final int LONGITUD_MAXIMA_CLAVE = 100;
    static final String ENCABEZADO_IDEMPOTENCIA = "Idempotency-Key";

    private final ReservaService reservaService;

    public ReservaController(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HUESPED')")
    @Operation(summary = "Reservar un alojamiento",
            description = "Crea una reserva PENDIENTE_PAGO para el Huésped autenticado y ocupa las fechas hasta que "
                    + "se pague o venza el plazo (fechaExpiracion). Calcula el precio con el descuento por estadía "
                    + "larga, la tarifa de limpieza y la tarifa de servicio, y conserva la política de cancelación "
                    + "vigente. Exige el encabezado Idempotency-Key, único por solicitud: si se reenvía la misma "
                    + "clave con la misma solicitud, responde 200 con la reserva ya creada en lugar de crear otra; "
                    + "con una solicitud distinta, 409. Si dos Huéspedes piden las mismas noches, solo uno las "
                    + "obtiene. Solo rol HUESPED.")
    @ApiResponse(responseCode = "201", description = "Reserva creada PENDIENTE_PAGO",
            content = @Content(schema = @Schema(implementation = ReservaResponse.class)))
    @ApiResponse(responseCode = "200", description = "Repetición de una solicitud ya procesada con la misma clave",
            content = @Content(schema = @Schema(implementation = ReservaResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos, llegada anterior a hoy, huéspedes por encima de la capacidad o falta "
                    + "la clave de idempotencia; no se guarda nada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Huésped",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "El alojamiento no existe o no está disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "Las fechas no están disponibles o la clave de idempotencia ya se usó con otra solicitud",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ReservaResponse> crear(
            @Valid @RequestBody CrearReservaRequest request,
            @RequestHeader(name = ENCABEZADO_IDEMPOTENCIA, required = false)
            @NotBlank(message = "El encabezado Idempotency-Key es obligatorio")
            @Size(max = LONGITUD_MAXIMA_CLAVE,
                    message = "El encabezado Idempotency-Key admite hasta " + LONGITUD_MAXIMA_CLAVE + " caracteres")
            String claveIdempotencia,
            @AuthenticationPrincipal UsuarioAutenticado huesped) {
        ReservaService.ResultadoCreacion resultado = reservaService.crear(request, claveIdempotencia, huesped);
        HttpStatus estado = resultado.creada() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(resultado.reserva());
    }
}
