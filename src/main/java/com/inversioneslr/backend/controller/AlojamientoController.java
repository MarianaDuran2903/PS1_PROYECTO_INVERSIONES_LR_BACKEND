package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.dto.request.FiltroAlojamientosRequest;
import com.inversioneslr.backend.dto.response.*;
import com.inversioneslr.backend.service.AlojamientoService;
import com.inversioneslr.backend.service.CatalogoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
                    + "de la más reciente a la más antigua. Permite combinar ciudad, "
                    + "huéspedes, fechas, tipo de alojamiento, precio por noche y servicios. "
                    + "Los límites de precio son inclusivos y pueden indicarse por separado. "
                    + "Si se indican ambos, el máximo debe ser mayor o igual al mínimo. "
                    + "Se exigen todos los servicios seleccionados; sus identificadores "
                    + "pueden enviarse repitiendo idsServicios en la consulta. "
                    + "Las fechas se indican juntas y la salida debe ser posterior a la llegada. "
                    + "Con fechas se exige disponibilidad registrada para todas las noches "
                    + "y ausencia de reservas que ocupen ese rango. "
                    + "Sin filtros conserva el listado general.")
    @ApiResponse(responseCode = "200", description = "Página de alojamientos (puede estar vacía)",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Filtros o paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaginaResponse<AlojamientoResumenResponse>> listar(
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

    @GetMapping("/{idPublicacion}")
    @SecurityRequirements
    @Operation(
            summary = "Consultar el detalle público de un alojamiento",
            description = "Devuelve únicamente publicaciones ACTIVAS. "
                    + "La ubicación pública incluye ciudad y barrio o zona, "
                    + "sin dirección exacta. No calcula disponibilidad ni cotización. "
                    + "Una publicación inexistente o no activa responde "
                    + "con el mismo mensaje de alojamiento no disponible.")
    @ApiResponse(
            responseCode = "200",
            description = "Detalle público del alojamiento",
            content = @Content(schema = @Schema(implementation = AlojamientoDetalleResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Identificador inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "404",
            description = "El alojamiento no está disponible",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<AlojamientoDetalleResponse> obtenerDetalle(@PathVariable("idPublicacion") @Min(value = 1, message = "El identificador debe ser mayor que cero") Long idPublicacion) {
        return ResponseEntity.ok(alojamientoService.obtenerDetalle(idPublicacion));
    }

    @GetMapping("/ciudades")
    @Operation(summary = "Listar ciudades para la búsqueda pública",
            description = "Devuelve las ciudades ordenadas por nombre. No exige autenticación.")
    @ApiResponse(responseCode = "200", description = "Catálogo público de ciudades")
    public ResponseEntity<List<CatalogoItemResponse>> listarCiudades() {
        return ResponseEntity.ok(catalogoService.listarCiudades());
    }
}
