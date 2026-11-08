package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Catálogos", description = "Listas de valores para los formularios")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class CatalogoController {

    private final CatalogoService catalogoService;

    public CatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/ciudades")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar ciudades", description = "Ciudades ordenadas por nombre. Cualquier rol autenticado.")
    @ApiResponse(responseCode = "200", description = "Ciudades",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CatalogoItemResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<CatalogoItemResponse>> listarCiudades() {
        return ResponseEntity.ok(catalogoService.listarCiudades());
    }

    @GetMapping("/tipos-alojamiento")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Listar tipos de alojamiento",
            description = "Tipos de alojamiento ordenados por nombre. Cualquier rol autenticado.")
    @ApiResponse(responseCode = "200", description = "Tipos de alojamiento",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = CatalogoItemResponse.class))))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<List<CatalogoItemResponse>> listarTiposAlojamiento() {
        return ResponseEntity.ok(catalogoService.listarTiposAlojamiento());
    }
}
