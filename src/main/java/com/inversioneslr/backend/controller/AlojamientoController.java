package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.dto.request.FiltroAlojamientosRequest;
import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.service.AlojamientoService;
import com.inversioneslr.backend.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consulta pública: no exige token, la usan Visitantes y Huéspedes.
 */
@RestController
@RequestMapping("/api/v1/alojamientos")
@Tag(name = "Alojamientos", description = "Consulta pública de los alojamientos disponibles")
public class AlojamientoController {

    private final AlojamientoService alojamientoService;
    private final CatalogoService catalogoService;

    public AlojamientoController(AlojamientoService alojamientoService, CatalogoService catalogoService) {
        this.alojamientoService = alojamientoService;
        this.catalogoService = catalogoService;
    }

    @GetMapping
    @Operation(summary = "Buscar alojamientos disponibles",
            description = "Consulta pública paginada de publicaciones ACTIVAS, "
                    + "de la más reciente a la más antigua. Permite filtrar por ciudad, "
                    + "cantidad de huéspedes y fechas. Las fechas se indican juntas y "
                    + "la salida debe ser posterior a la llegada. Con fechas se exige "
                    + "disponibilidad registrada para todas las noches y ausencia de "
                    + "reservas que ocupen ese rango. Sin filtros conserva el listado general.")
    @ApiResponse(responseCode = "200", description = "Página de alojamientos (puede estar vacía)",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Filtros o paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaginaResponse<PublicacionResumenResponse>> listar(
            @Parameter(description = "Número de página, empezando en 0", example = "0")
            @RequestParam(name = "pagina", defaultValue = "0")
            @Min(value = 0, message = "La página debe ser 0 o mayor") int pagina,
            @Parameter(description = "Elementos por página (1 a " + PublicacionController.TAMANO_MAXIMO + ")",
                    example = "20")
            @RequestParam(name = "tamano", defaultValue = "20")
            @Min(value = 1, message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO)
            @Max(value = PublicacionController.TAMANO_MAXIMO,
                    message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO) int tamano,
            @ParameterObject @Valid @ModelAttribute FiltroAlojamientosRequest filtros) {
        return ResponseEntity.ok(alojamientoService.listarDisponibles(pagina, tamano, filtros));
    }

    @GetMapping("/ciudades")
    @Operation(summary = "Listar ciudades para la búsqueda pública",
            description = "Devuelve las ciudades ordenadas por nombre. No exige autenticación.")
    @ApiResponse(responseCode = "200", description = "Catálogo público de ciudades")
    public ResponseEntity<List<CatalogoItemResponse>> listarCiudades() {
        return ResponseEntity.ok(catalogoService.listarCiudades());
    }
}
