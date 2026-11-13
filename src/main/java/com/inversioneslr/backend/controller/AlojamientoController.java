package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.service.AlojamientoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta pública: no exige token, la usan Visitantes y Huéspedes.
 */
@RestController
@RequestMapping("/api/v1/alojamientos")
@Tag(name = "Alojamientos", description = "Consulta pública de los alojamientos disponibles")
public class AlojamientoController {

    private final AlojamientoService alojamientoService;

    public AlojamientoController(AlojamientoService alojamientoService) {
        this.alojamientoService = alojamientoService;
    }

    @GetMapping
    @Operation(summary = "Listar alojamientos disponibles",
            description = "Devuelve, paginados y del más reciente al más antiguo, los alojamientos disponibles "
                    + "para nuevas reservas: solo publicaciones ACTIVAS. Una publicación pausada, bloqueada, en "
                    + "revisión o en borrador no aparece. No exige autenticación. Si no hay ninguno, la lista "
                    + "viene vacía y el campo mensaje lo informa.")
    @ApiResponse(responseCode = "200", description = "Página de alojamientos (puede estar vacía)",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos",
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
                    message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO) int tamano) {
        return ResponseEntity.ok(alojamientoService.listarDisponibles(pagina, tamano));
    }
}
