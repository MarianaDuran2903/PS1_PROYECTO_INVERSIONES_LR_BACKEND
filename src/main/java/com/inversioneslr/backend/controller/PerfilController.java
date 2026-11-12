package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.ActualizarEstadoCuentaRequest;
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
import org.springframework.web.bind.annotation.*;
import com.inversioneslr.backend.dto.request.ActualizarPerfilRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Perfil", description = "Consulta y actualización del perfil de la cuenta autenticada")
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

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    @Operation(
            summary = "Actualizar mi perfil",
            description = "Actualiza únicamente los nombres y apellidos de la cuenta autenticada. "
                    + "Disponible para Huésped, Anfitrión y Administrador "
                    + "(MAR-136, HU-006, RF-USR-006). "
                    + "No permite cambiar correo, contraseña, rol ni estado de cuenta. "
                    + "La cuenta se identifica mediante la sesión, no mediante el cuerpo de la petición.")
    @ApiResponse(
            responseCode = "200",
            description = "Perfil actualizado",
            content = @Content(
                    schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Datos inválidos o incompletos",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Sin autenticación válida o cuenta no habilitada",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> actualizarPerfil(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody ActualizarPerfilRequest request
    ) {
        return ResponseEntity.ok(
                perfilService.actualizarPerfil(usuario.idUsuario(), request)
        );
    }

    @PatchMapping("/me/estado")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    @Operation(
            summary = "Desactivar mi cuenta",
            description = "Desactiva únicamente la cuenta autenticada. "
                    + "Disponible para Huésped, Anfitrión y Administrador "
                    + "(MAR-137, HU-007, RF-USR-007). "
                    + "La cuenta se identifica mediante la sesión, no mediante el cuerpo de la petición. "
                    + "La reactivación posterior requiere iniciar sesión con credenciales válidas.")
    @ApiResponse(
            responseCode = "200",
            description = "Cuenta desactivada",
            content = @Content(
                    schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Estado solicitado no permitido o datos inválidos",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "401",
            description = "Sin autenticación válida o cuenta no habilitada",
            content = @Content(
                    schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> cambiarEstado(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody ActualizarEstadoCuentaRequest request
    ) {
        return ResponseEntity.ok(
                perfilService.cambiarEstado(usuario.idUsuario(), request)
        );
    }

}