package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PagoResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PagoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservas/{idReserva}/pago")
@Tag(name = "Pagos", description = "Pago de una reserva con Stripe en modo de pruebas")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class PagoController {

    private final PagoService pagoService;

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HUESPED')")
    @Operation(summary = "Iniciar el pago de una reserva",
            description = "Abre una sesión de pago de Stripe (modo de pruebas, sin cobro real) por el total de una "
                    + "reserva PENDIENTE_PAGO del Huésped autenticado y devuelve urlPago, a donde hay que llevar al "
                    + "Huésped. El monto es siempre el de la reserva. Mientras paga, el plazo de la reserva se "
                    + "alarga para que no venza. Si ya hay una sesión abierta, responde 200 con la misma en lugar "
                    + "de abrir otra. La reserva solo queda CONFIRMADA cuando Stripe informa el cobro; volver a la "
                    + "página de éxito no la confirma. Solo rol HUESPED.")
    @ApiResponse(responseCode = "201", description = "Sesión de pago abierta",
            content = @Content(schema = @Schema(implementation = PagoResponse.class)))
    @ApiResponse(responseCode = "200", description = "Ya había una sesión abierta y se devuelve la misma",
            content = @Content(schema = @Schema(implementation = PagoResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no es Huésped o la reserva es de otro Huésped",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La reserva no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "La reserva ya está confirmada, venció, se canceló o agotó sus intentos de pago",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "502", description = "No se pudo comunicar con Stripe, o los pagos no están configurados",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PagoResponse> iniciar(@PathVariable Long idReserva,
                                                @AuthenticationPrincipal UsuarioAutenticado huesped) {
        PagoService.ResultadoInicio resultado = pagoService.iniciar(idReserva, huesped);
        HttpStatus estado = resultado.creado() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(estado).body(resultado.pago());
    }

    @GetMapping
    @PreAuthorize("hasRole('HUESPED')")
    @Operation(summary = "Consultar el resultado del pago de una reserva",
            description = "Devuelve el pago más reciente de una reserva del Huésped autenticado: su estado "
                    + "(PENDIENTE, APROBADO, RECHAZADO o EXPIRADO), el estado de la reserva y, si fue rechazado, el "
                    + "motivo. Es la consulta de la pantalla de resultado: si el pago sigue abierto, antes consulta "
                    + "a Stripe por si el aviso aún no llegó. Mientras la sesión admita el pago incluye urlPago "
                    + "para reintentar. Solo rol HUESPED.")
    @ApiResponse(responseCode = "200", description = "Estado del pago",
            content = @Content(schema = @Schema(implementation = PagoResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no es Huésped o la reserva es de otro Huésped",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La reserva no existe o todavía no tiene un pago iniciado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PagoResponse consultar(@PathVariable Long idReserva,
                                  @AuthenticationPrincipal UsuarioAutenticado huesped) {
        return pagoService.consultar(idReserva, huesped);
    }
}
