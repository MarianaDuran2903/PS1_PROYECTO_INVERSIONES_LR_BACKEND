package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PublicacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/publicaciones")
@Tag(name = "Publicaciones", description = "Gestión de publicaciones de alojamientos por el Anfitrión")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class PublicacionController {

    static final int TAMANO_MAXIMO = 100;

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

    @GetMapping("/mias")
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(summary = "Listar mis publicaciones",
            description = "Devuelve, paginadas y de la más reciente a la más antigua, únicamente las publicaciones "
                    + "del Anfitrión autenticado con su estado vigente. Si no tiene ninguna, la lista viene vacía "
                    + "y el campo mensaje lo informa. Solo rol ANFITRION.")
    @ApiResponse(responseCode = "200", description = "Página de publicaciones (puede estar vacía)",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Anfitrión",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaginaResponse<PublicacionResumenResponse>> listarPropias(
            @Parameter(description = "Número de página, empezando en 0", example = "0")
            @RequestParam(name = "pagina", defaultValue = "0")
            @Min(value = 0, message = "La página debe ser 0 o mayor") int pagina,
            @Parameter(description = "Elementos por página (1 a " + TAMANO_MAXIMO + ")", example = "20")
            @RequestParam(name = "tamano", defaultValue = "20")
            @Min(value = 1, message = "El tamaño debe estar entre 1 y " + TAMANO_MAXIMO)
            @Max(value = TAMANO_MAXIMO, message = "El tamaño debe estar entre 1 y " + TAMANO_MAXIMO) int tamano,
            @AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return ResponseEntity.ok(publicacionService.listarPropias(anfitrion, pagina, tamano));
    }

    @GetMapping("/{idPublicacion}")
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(summary = "Consultar una publicación propia",
            description = "Devuelve una publicación del Anfitrión autenticado, por ejemplo para precargar el "
                    + "formulario de edición. Solo rol ANFITRION y solo el propietario.")
    @ApiResponse(responseCode = "200", description = "Publicación encontrada",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400", description = "El identificador no es un número",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403",
            description = "El usuario no tiene rol Anfitrión o la publicación pertenece a otro Anfitrión",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> obtener(@PathVariable Long idPublicacion,
                                                       @AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return ResponseEntity.ok(publicacionService.obtenerPropia(idPublicacion, anfitrion));
    }

    @PutMapping("/{idPublicacion}")
    @PreAuthorize("hasRole('ANFITRION')")
    @Operation(summary = "Editar una publicación propia",
            description = "Reemplaza la información editable de una publicación del Anfitrión autenticado. "
                    + "Solo se permite en BORRADOR o PAUSADA y no cambia el estado; una publicación ACTIVA debe "
                    + "pausarse primero. Solo rol ANFITRION y solo el propietario.")
    @ApiResponse(responseCode = "200", description = "Cambios guardados",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Datos inválidos, ciudad o tipo inexistente; la publicación conserva lo que tenía",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403",
            description = "El usuario no tiene rol Anfitrión o la publicación pertenece a otro Anfitrión",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409",
            description = "El estado no permite editar (ACTIVA, PENDIENTE_REVISION o BLOQUEADA)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> actualizar(@PathVariable Long idPublicacion,
                                                          @Valid @RequestBody ActualizarPublicacionRequest request,
                                                          @AuthenticationPrincipal UsuarioAutenticado anfitrion) {
        return ResponseEntity.ok(publicacionService.actualizar(idPublicacion, request, anfitrion));
    }
}
