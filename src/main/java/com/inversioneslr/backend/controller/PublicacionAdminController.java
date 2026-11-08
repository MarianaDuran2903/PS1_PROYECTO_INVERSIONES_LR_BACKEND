package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PublicacionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/publicaciones")
@Tag(name = "Administración de publicaciones", description = "Intervención del Administrador sobre publicaciones")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class PublicacionAdminController {

    private final PublicacionAdminService publicacionAdminService;

    public PublicacionAdminController(PublicacionAdminService publicacionAdminService) {
        this.publicacionAdminService = publicacionAdminService;
    }

    @GetMapping("/{idPublicacion}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Consultar una publicación (Administrador)",
            description = "Devuelve cualquier publicación, de cualquier Anfitrión, para revisarla o precargar el "
                    + "formulario de intervención. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Publicación encontrada",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400", description = "El identificador no es un número",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> obtener(@PathVariable Long idPublicacion) {
        return ResponseEntity.ok(publicacionAdminService.obtener(idPublicacion));
    }

    @PutMapping("/{idPublicacion}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Intervenir una publicación (Administrador)",
            description = "Reemplaza la información editable de una publicación con un motivo obligatorio que queda "
                    + "registrado junto con el Administrador y la fecha. No cambia el estado ni el propietario. "
                    + "Una publicación ACTIVA debe pausarse antes. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Cambios guardados y la intervención registrada",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos, motivo ausente o fuera de 10 a 500 caracteres, ciudad o tipo inexistente, "
                    + "o reglas vacías en una publicación en revisión; la publicación no cambia",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La publicación está ACTIVA y debe pausarse antes",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> intervenir(@PathVariable Long idPublicacion,
                                                          @Valid @RequestBody EdicionAdministrativaRequest request,
                                                          @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.intervenir(idPublicacion, request, administrador));
    }
}
