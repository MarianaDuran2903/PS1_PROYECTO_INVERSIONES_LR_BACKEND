package com.inversioneslr.backend.exception;

import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String ENTIDAD_RUTA = "Ruta";
    static final String MOTIVO_ROL_NO_AUTORIZADO = "ROL_NO_AUTORIZADO";

    private final AuditoriaService auditoriaService;

    public GlobalExceptionHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex,
                                                           HttpServletRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return construir(HttpStatus.BAD_REQUEST, "Los datos enviados no son válidos", request, errores);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", request, null);
    }

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ResponseEntity<ErrorResponse> manejarCorreoDuplicado(CorreoYaRegistradoException ex,
                                                                HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> manejarCredencialesInvalidas(CredencialesInvalidasException ex,
                                                                      HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> manejarNoAutenticado(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión", request, null);
    }

    /**
     * Un usuario autenticado pidió una operación que su rol no permite (@PreAuthorize): se deniega y se registra
     * el intento para trazabilidad (HU-003).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccessDeniedException ex,
                                                               HttpServletRequest request) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            auditoriaService.registrarIntentoFallido(usuario.idUsuario(), usuario.correo(),
                    AccionAuditoria.ACCESO_DENEGADO, ENTIDAD_RUTA,
                    request.getMethod() + " " + request.getRequestURI(), MOTIVO_ROL_NO_AUTORIZADO);
        }
        return construir(HttpStatus.FORBIDDEN, "No tiene permisos para esta operación", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorInesperado(Exception ex, HttpServletRequest request) {
        // RF-TRZ-006: registro de errores. El detalle técnico queda en el log, no en la respuesta.
        log.error("Error no controlado {} {}",
                kv("accion", "ERROR_NO_CONTROLADO"),
                kv("resultado", "FALLIDO"),
                kv("ruta", request.getRequestURI()),
                ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", request, null);
    }

    private static ResponseEntity<ErrorResponse> construir(HttpStatus estado, String mensaje,
                                                           HttpServletRequest request,
                                                           Map<String, String> errores) {
        ErrorResponse cuerpo = new ErrorResponse(
                OffsetDateTime.now(),
                estado.value(),
                estado.getReasonPhrase(),
                mensaje,
                request.getRequestURI(),
                errores);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
