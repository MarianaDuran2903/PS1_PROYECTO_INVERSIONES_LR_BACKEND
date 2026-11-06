package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PerfilService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Perfil", description = "Consulta del perfil propio")
public class PerfilController {

    private final PerfilService perfilService;

    public PerfilController(PerfilService perfilService) {
        this.perfilService = perfilService;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    @Operation(
            summary = "Consultar mi perfil",
            description = "Devuelve los datos básicos de la cuenta autenticada. "
                    + "Disponible para Huésped, Anfitrión y Administrador "
                    + "(MAR-135, HU-005, RF-USR-005). "
                    + "No devuelve credenciales ni datos de otras cuentas.")
    @ApiResponse(
            responseCode = "200",
            description = "Perfil propio",
            content = @Content(
                    schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Sin autenticación válida o cuenta no habilitada",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> consultarPerfil(
            @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(
                perfilService.consultarPerfil(usuario.idUsuario()));
    }
}