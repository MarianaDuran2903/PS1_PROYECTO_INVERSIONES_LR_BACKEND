package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
}