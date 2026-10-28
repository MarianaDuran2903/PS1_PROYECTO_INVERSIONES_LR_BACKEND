package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.dto.request.RegistroHuespedRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.service.RegistroUsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación", description = "Registro e inicio de sesión de usuarios")
public class AutenticacionController {

    private final RegistroUsuarioService registroUsuarioService;

    public AutenticacionController(RegistroUsuarioService registroUsuarioService) {
        this.registroUsuarioService = registroUsuarioService;
    }

    @PostMapping("/registro/huesped")
    @Operation(summary = "Registrar Huésped",
            description = "Crea una cuenta con rol HUESPED. Ruta pública (RF-USR-001).")
    @ApiResponse(responseCode = "201", description = "Cuenta creada con rol Huésped",
            content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
    @ApiResponse(responseCode = "400", description = "Datos inválidos o JSON mal formado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> registrarHuesped(@Valid @RequestBody RegistroHuespedRequest request) {
        UsuarioResponse respuesta = registroUsuarioService.registrarHuesped(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }
}
