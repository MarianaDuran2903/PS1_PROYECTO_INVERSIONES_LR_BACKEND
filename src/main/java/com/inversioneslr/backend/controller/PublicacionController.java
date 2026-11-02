package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PublicacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/publicaciones")
@Tag(name = "Publicaciones", description = "Gestión de publicaciones de alojamientos por el Anfitrión")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class PublicacionController {

    private final PublicacionService publicacionService;

    public PublicacionController(PublicacionService publicacionService) {
        this.publicacionService = publicacionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(summary = "Crear publicación",
            description = "Crea una publicación en estado BORRADOR asociada al Anfitrión autenticado. "
                    + "Solo rol ANFITRION.")
    @ApiResponse(responseCode = "201", description = "Publicación guardada en BORRADOR",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos o incompletos, ciudad o tipo inexistente; no se guarda nada",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Anfitrión",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> crear(@Valid @RequestBody CrearPublicacionRequest request,
                                                     @AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return ResponseEntity.status(HttpStatus.CREATED).body(publicacionService.crear(request, anfitrion));
    }
}
