package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.config.OpenApiConfig;
import com.inversioneslr.backend.dto.request.BloqueoPublicacionRequest;
import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.request.ReactivacionPublicacionRequest;
import com.inversioneslr.backend.dto.request.RechazoPublicacionRequest;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PublicacionAdminService;
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
@RequestMapping("/api/v1/admin/publicaciones")
@Tag(name = "Administración de publicaciones",
        description = "Revisión e intervención del Administrador sobre publicaciones")
@SecurityRequirement(name = OpenApiConfig.ESQUEMA_BEARER)
public class PublicacionAdminController {

    private final PublicacionAdminService publicacionAdminService;

    public PublicacionAdminController(PublicacionAdminService publicacionAdminService) {
        this.publicacionAdminService = publicacionAdminService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar publicaciones por estado (Administrador)",
            description = "Devuelve, paginadas y de la más reciente a la más antigua, las publicaciones de cualquier "
                    + "Anfitrión con la información completa, incluido el motivo del bloqueo. El parámetro estado "
                    + "(BORRADOR, PENDIENTE_REVISION, ACTIVA, PAUSADA o BLOQUEADA, en mayúsculas) filtra por "
                    + "estado; sin él devuelve todas. Sirve para encontrar las publicaciones pausadas o bloqueadas "
                    + "que se pueden reactivar. Si no hay ninguna, la lista viene vacía y el campo mensaje lo "
                    + "informa. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Página de publicaciones (puede estar vacía)",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Estado o parámetros de paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaginaResponse<PublicacionResponse>> listar(
            @Parameter(description = "Estado por el que filtrar; sin valor se devuelven todas", example = "BLOQUEADA")
            @RequestParam(name = "estado", required = false) EstadoPublicacion estado,
            @Parameter(description = "Número de página, empezando en 0", example = "0")
            @RequestParam(name = "pagina", defaultValue = "0")
            @Min(value = 0, message = "La página debe ser 0 o mayor") int pagina,
            @Parameter(description = "Elementos por página (1 a " + PublicacionController.TAMANO_MAXIMO + ")",
                    example = "20")
            @RequestParam(name = "tamano", defaultValue = "20")
            @Min(value = 1, message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO)
            @Max(value = PublicacionController.TAMANO_MAXIMO,
                    message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO) int tamano,
            @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.listar(administrador, estado, pagina, tamano));
    }

    @GetMapping("/pendientes")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Listar publicaciones pendientes de revisión",
            description = "Devuelve, paginadas y de la más antigua a la más reciente, las publicaciones en "
                    + "PENDIENTE_REVISION con la información completa para decidir si se aprueban o se rechazan. "
                    + "Si no hay ninguna, la lista viene vacía y el campo mensaje lo informa. "
                    + "Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Página de publicaciones pendientes (puede estar vacía)",
            content = @Content(schema = @Schema(implementation = PaginaResponse.class)))
    @ApiResponse(responseCode = "400", description = "Parámetros de paginación inválidos",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PaginaResponse<PublicacionResponse>> listarPendientes(
            @Parameter(description = "Número de página, empezando en 0", example = "0")
            @RequestParam(name = "pagina", defaultValue = "0")
            @Min(value = 0, message = "La página debe ser 0 o mayor") int pagina,
            @Parameter(description = "Elementos por página (1 a " + PublicacionController.TAMANO_MAXIMO + ")",
                    example = "20")
            @RequestParam(name = "tamano", defaultValue = "20")
            @Min(value = 1, message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO)
            @Max(value = PublicacionController.TAMANO_MAXIMO,
                    message = "El tamaño debe estar entre 1 y " + PublicacionController.TAMANO_MAXIMO) int tamano,
            @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.listarPendientes(administrador, pagina, tamano));
    }

    @PostMapping("/{idPublicacion}/aprobar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Aprobar una publicación pendiente",
            description = "Pasa una publicación de PENDIENTE_REVISION a ACTIVA y registra la decisión junto con "
                    + "el Administrador y la fecha. Si le falta información mínima, informa todos los pendientes "
                    + "y la publicación conserva su estado. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Publicación ACTIVA",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Falta información mínima: el detalle trae cada campo pendiente, o el identificador "
                    + "no es un número",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La publicación no está en PENDIENTE_REVISION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> aprobar(@PathVariable Long idPublicacion,
                                                       @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.aprobar(idPublicacion, administrador));
    }

    @PostMapping("/{idPublicacion}/rechazar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Rechazar una publicación pendiente",
            description = "Devuelve una publicación de PENDIENTE_REVISION a BORRADOR para que el Anfitrión la "
                    + "corrija y la reenvíe. El motivo es obligatorio y queda registrado junto con el "
                    + "Administrador y la fecha. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Publicación devuelta a BORRADOR",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Motivo ausente o fuera de 10 a 500 caracteres, o el identificador no es un número; "
                    + "la publicación no cambia",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La publicación no está en PENDIENTE_REVISION",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> rechazar(@PathVariable Long idPublicacion,
                                                        @Valid @RequestBody RechazoPublicacionRequest request,
                                                        @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.rechazar(idPublicacion, request, administrador));
    }

    @PostMapping("/{idPublicacion}/bloquear")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Bloquear una publicación engañosa",
            description = "Pasa una publicación en revisión, activa o pausada a BLOQUEADA y deja de ofrecerse en "
                    + "las búsquedas. El motivo es obligatorio: se guarda en la publicación, que lo devuelve en "
                    + "motivoBloqueo, y queda registrado junto con el Administrador y la fecha. Un borrador no se "
                    + "bloquea y una publicación ya bloqueada tampoco. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Publicación BLOQUEADA",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Motivo ausente o fuera de 10 a 500 caracteres, o el identificador no es un número; "
                    + "la publicación no cambia",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La publicación es un borrador o ya está BLOQUEADA",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> bloquear(@PathVariable Long idPublicacion,
                                                        @Valid @RequestBody BloqueoPublicacionRequest request,
                                                        @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.bloquear(idPublicacion, request, administrador));
    }

    @PostMapping("/{idPublicacion}/reactivar")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Reactivar una publicación pausada o bloqueada",
            description = "Devuelve a ACTIVA una publicación PAUSADA, tras revisar el ajuste del Anfitrión, o "
                    + "BLOQUEADA, según el motivo del bloqueo, y vuelve a ofrecerse en las búsquedas. El motivo "
                    + "de la reactivación es obligatorio y queda registrado junto con el estado de origen, el "
                    + "Administrador y la fecha; el motivo del bloqueo se limpia. Si le falta información mínima, "
                    + "informa todos los pendientes y la publicación conserva su estado. Solo rol ADMINISTRADOR.")
    @ApiResponse(responseCode = "200", description = "Publicación ACTIVA",
            content = @Content(schema = @Schema(implementation = PublicacionResponse.class)))
    @ApiResponse(responseCode = "400",
            description = "Falta información mínima: el detalle trae cada campo pendiente; o motivo ausente o "
                    + "fuera de 10 a 500 caracteres, o el identificador no es un número",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Sin token o token inválido",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El usuario no tiene rol Administrador",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La publicación no existe",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La publicación no está PAUSADA ni BLOQUEADA",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<PublicacionResponse> reactivar(@PathVariable Long idPublicacion,
                                                         @Valid @RequestBody ReactivacionPublicacionRequest request,
                                                         @AuthenticationPrincipal UsuarioAutenticado administrador) {
        return ResponseEntity.ok(publicacionAdminService.reactivar(idPublicacion, request, administrador));
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
