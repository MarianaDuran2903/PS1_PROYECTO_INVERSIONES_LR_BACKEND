package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
import com.inversioneslr.backend.dto.request.AbrirPeriodoDisponibilidadRequest;
import com.inversioneslr.backend.dto.response.PeriodoDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.DisponibilidadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/publicaciones")
@Tag(name = "Disponibilidad", description = "Consulta del calendario propio del Anfitrión")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    public DisponibilidadController(DisponibilidadService disponibilidadService) {
        this.disponibilidadService = disponibilidadService;
    }

    @GetMapping("/{idPublicacion}/disponibilidad")
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(
            summary = "Consultar calendario propio",
            description = "Devuelve los periodos de una publicación del Anfitrión autenticado."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Calendario consultado",
            content = @Content(
                    schema = @Schema(implementation = CalendarioDisponibilidadResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "Identificador inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "403",
            description = "La publicación pertenece a otro usuario",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
    )
    public ResponseEntity<CalendarioDisponibilidadResponse> consultar(
            @PathVariable Long idPublicacion,
            @AuthenticationPrincipal UsuarioAutenticado anfitrion
    ) {
        return ResponseEntity.ok(
                disponibilidadService.consultarCalendarioPropio(idPublicacion, anfitrion)
        );
    }

    @PostMapping("/{idPublicacion}/disponibilidad")
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(summary = "Abrir un periodo disponible", description = "Registra un rango disponible en el calendario propio.")
    public ResponseEntity<PeriodoDisponibilidadResponse> abrirPeriodo(
            @PathVariable Long idPublicacion,
            @Valid @RequestBody AbrirPeriodoDisponibilidadRequest request,
            @AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(disponibilidadService.abrirPeriodoPropio(idPublicacion, request, anfitrion));
    }

    @PatchMapping("/{idPublicacion}/disponibilidad/{idPeriodo}/no-ofrecido")
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(summary = "Cerrar un periodo disponible",
            description = "Marca como no ofrecido un periodo disponible de una publicación propia.")
    @ApiResponse(responseCode = "200", description = "Periodo cerrado")
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido")
    @ApiResponse(responseCode = "403", description = "La publicación pertenece a otro usuario")
    @ApiResponse(responseCode = "404", description = "La publicación no existe")
    @ApiResponse(responseCode = "409", description = "La publicación está bloqueada o el periodo ya no está disponible")
    public ResponseEntity<PeriodoDisponibilidadResponse> cerrarPeriodo(
            @PathVariable Long idPublicacion,
            @PathVariable Long idPeriodo,
            @AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return ResponseEntity.ok(disponibilidadService.cerrarPeriodoPropio(idPublicacion, idPeriodo, anfitrion));
    }
}
