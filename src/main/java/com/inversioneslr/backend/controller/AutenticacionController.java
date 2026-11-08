package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.LoginRequest;
import com.inversioneslr.backend.dto.request.RegistroUsuarioRequest;
import com.inversioneslr.backend.dto.request.RestablecerContrasenaRequest;
import com.inversioneslr.backend.dto.request.SolicitudRecuperacionRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.LoginResponse;
import com.inversioneslr.backend.dto.response.MensajeResponse;
import com.inversioneslr.backend.dto.response.SesionResponse;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AutenticacionService;
import com.inversioneslr.backend.service.RecuperacionContrasenaService;
import com.inversioneslr.backend.service.RegistroUsuarioService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro, inicio de sesión y recuperación de contraseña")
public class AutenticacionController {

    private final RegistroUsuarioService registroUsuarioService;
    private final AutenticacionService autenticacionService;
    private final RecuperacionContrasenaService recuperacionContrasenaService;

    public AutenticacionController(RegistroUsuarioService registroUsuarioService,
                                   AutenticacionService autenticacionService,
                                   RecuperacionContrasenaService recuperacionContrasenaService) {
        this.registroUsuarioService = registroUsuarioService;
        this.autenticacionService = autenticacionService;
        this.recuperacionContrasenaService = recuperacionContrasenaService;
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesión",
            description = "Valida las credenciales y devuelve un JWT con el rol del usuario (RF-USR-002, HU-003). "
                    + "Ruta pública.")
    @ApiResponse(responseCode = "200", description = "Sesión creada",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "400", description = "Falta el correo o la contraseña",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Correo o contraseña incorrectos (mensaje genérico)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<LoginResponse> iniciarSesion(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(autenticacionService.iniciarSesion(request));
    }

    @PostMapping("/recuperacion/solicitud")
    @Operation(summary = "Solicitar recuperación de contraseña",
            description = "Inicia la recuperación simulada: si el correo pertenece a una cuenta activa, genera un "
                    + "enlace de un solo uso y lo deja en el log del servidor en lugar de enviar un correo. "
                    + "La respuesta es la misma exista o no el correo. Ruta pública.")
    @ApiResponse(responseCode = "202", description = "Solicitud recibida (mismo mensaje para cualquier correo)",
            content = @Content(schema = @Schema(implementation = MensajeResponse.class)))
    @ApiResponse(responseCode = "400", description = "Falta el correo o no tiene un formato válido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MensajeResponse> solicitarRecuperacion(
            @Valid @RequestBody SolicitudRecuperacionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(recuperacionContrasenaService.solicitar(request));
    }

    @PostMapping("/recuperacion/restablecer")
    @Operation(summary = "Restablecer contraseña",
            description = "Establece una nueva contraseña con el token de recuperación. El token vence a los "
                    + "minutos configurados y solo se puede usar una vez. La contraseña sigue las reglas del "
                    + "registro. Ruta pública.")
    @ApiResponse(responseCode = "200", description = "Contraseña actualizada",
            content = @Content(schema = @Schema(implementation = MensajeResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos, o el token no existe, venció o ya se usó (mismo mensaje en los tres casos)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MensajeResponse> restablecerContrasena(
            @Valid @RequestBody RestablecerContrasenaRequest request) {
        return ResponseEntity.ok(recuperacionContrasenaService.restablecer(request));
    }

    @GetMapping("/sesion")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
    @Operation(summary = "Consultar la sesión activa",
            description = "Devuelve el usuario del token y su rol, para que el frontend mantenga la sesión al "
                    + "recargar la página. Cualquier rol autenticado.")
    @ApiResponse(responseCode = "200", description = "Sesión activa",
            content = @Content(schema = @Schema(implementation = SesionResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token, token vencido o inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SesionResponse> obtenerSesion(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return ResponseEntity.ok(autenticacionService.obtenerSesion(usuario));
    }

    @PostMapping("/registro/huesped")
    @Operation(summary = "Registrar Huésped",
            description = "Crea una cuenta con rol HUESPED. Ruta pública (RF-USR-001, HU-001).")
    @ApiResponse(responseCode = "201", description = "Cuenta creada con rol Huésped",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos, incompletos o JSON mal formado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> registrarHuesped(@Valid @RequestBody RegistroUsuarioRequest request) {
        UsuarioResponse respuesta = registroUsuarioService.registrarHuesped(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @PostMapping("/registro/anfitrion")
    @Operation(summary = "Registrar Anfitrión",
            description = "Crea una cuenta con rol ANFITRION. Ruta pública (RF-USR-001, HU-002).")
    @ApiResponse(responseCode = "201", description = "Cuenta creada con rol Anfitrión",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos, incompletos o JSON mal formado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> registrarAnfitrion(@Valid @RequestBody RegistroUsuarioRequest request) {
        UsuarioResponse respuesta = registroUsuarioService.registrarAnfitrion(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
