package com.inversioneslr.backend.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.method.ParameterValidationResult;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String ENTIDAD_RUTA = "Ruta";
    static final String MOTIVO_ROL_NO_AUTORIZADO = "ROL_NO_AUTORIZADO";
    private static final String MENSAJE_DATOS_INVALIDOS = "Los datos enviados no son válidos";

    private final AuditoriaService auditoriaService;

    public GlobalExceptionHandler(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(BindException ex, HttpServletRequest request) {

        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(error.getField(), mensajeDeCampo(error));
        }

        return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request, errores);
    }

    /**
     * JSON mal formado, o un campo con un tipo de dato incorrecto (por ejemplo, "capacidad": 2.5 o
     * "precioNoche": "abc"). En el segundo caso el error se informa en el campo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                               HttpServletRequest request) {
        if (ex.getCause() instanceof MismatchedInputException tipoIncorrecto) {
            String campo = tipoIncorrecto.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            if (!campo.isEmpty()) {
                return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request,
                        Map.of(campo, "El tipo de dato no es válido"));
            }
        }
        return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", request, null);
    }

    @ExceptionHandler(TokenRecuperacionInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarTokenRecuperacionInvalido(TokenRecuperacionInvalidoException ex,
                                                                          HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ReferenciaInvalidaException.class)
    public ResponseEntity<ErrorResponse> manejarReferenciaInvalida(ReferenciaInvalidaException ex,
                                                                   HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request,
                Map.of(ex.getCampo(), ex.getMessage()));
    }

    /**
     * Un parámetro de la ruta con un tipo incorrecto (por ejemplo, /publicaciones/abc).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarParametroInvalido(MethodArgumentTypeMismatchException ex,
                                                                  HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request,
                Map.of(ex.getName(), "El tipo de dato no es válido"));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> manejarParametrosInvalidos(HandlerMethodValidationException ex, HttpServletRequest request) {

        Map<String, String> errores = new LinkedHashMap<>();

        for (ParameterValidationResult resultado : ex.getAllValidationResults()) {
            if (resultado instanceof ParameterErrors campos) {
                for (FieldError error : campos.getFieldErrors()) {
                    errores.putIfAbsent(error.getField(), mensajeDeCampo(error));
                }
            } else {
                String parametro = nombreDelParametro(resultado.getMethodParameter());
                resultado.getResolvableErrors().stream().findFirst()
                        .ifPresent(error -> errores.putIfAbsent(
                                parametro,
                                Objects.requireNonNullElse(error.getDefaultMessage(), "El valor no es válido")));
            }
        }

        return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request, errores);
    }

    @ExceptionHandler(InformacionIncompletaException.class)
    public ResponseEntity<ErrorResponse> manejarInformacionIncompleta(InformacionIncompletaException ex,
                                                                      HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request,
                Map.of(ex.getCampo(), ex.getMessage()));
    }

    @ExceptionHandler(InformacionMinimaIncompletaException.class)
    public ResponseEntity<ErrorResponse> manejarInformacionMinimaIncompleta(InformacionMinimaIncompletaException ex,
                                                                            HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request, ex.getPendientes());
    }

    @ExceptionHandler(TransicionNoPermitidaException.class)
    public ResponseEntity<ErrorResponse> manejarTransicionNoPermitida(TransicionNoPermitidaException ex,
                                                                      HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(DatosReservaInvalidosException.class)
    public ResponseEntity<ErrorResponse> manejarDatosReservaInvalidos(DatosReservaInvalidosException ex,
                                                                      HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, MENSAJE_DATOS_INVALIDOS, request,
                Map.of(ex.getCampo(), ex.getMessage()));
    }

    @ExceptionHandler(FechasNoDisponiblesException.class)
    public ResponseEntity<ErrorResponse> manejarFechasNoDisponibles(FechasNoDisponiblesException ex,
                                                                    HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ClaveIdempotenciaReutilizadaException.class)
    public ResponseEntity<ErrorResponse> manejarClaveIdempotenciaReutilizada(ClaveIdempotenciaReutilizadaException ex,
                                                                             HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AlojamientoNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> manejarAlojamientoNoDisponible(AlojamientoNoDisponibleException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(PublicacionNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> manejarPublicacionNoEncontrada(PublicacionNoEncontradaException ex,
                                                                        HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(PublicacionAjenaException.class)
    public ResponseEntity<ErrorResponse> manejarPublicacionAjena(PublicacionAjenaException ex,
                                                                 HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    @ExceptionHandler(EstadoNoEditableException.class)
    public ResponseEntity<ErrorResponse> manejarEstadoNoEditable(EstadoNoEditableException ex,
                                                                 HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
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

    @ExceptionHandler(CuentaDesactivadaException.class)
    public ResponseEntity<ErrorResponse> manejarCuentaDesactivada(CuentaDesactivadaException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(EstadoCuentaInvalidoException.class)
    public ResponseEntity<ErrorResponse> manejarEstadoInvalido(EstadoCuentaInvalidoException ex,  HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> manejarNoAutenticado(AuthenticationException ex,
                                                              HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión", request, null);
    }

    /**
     * Un usuario autenticado pidió una operación que su rol no permite (@PreAuthorize): se deniega y se registra
     * el intento para trazabilidad.
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
        // Registro de errores. El detalle técnico queda en el log, no en la respuesta.
        log.error("Error no controlado {} {}",
                kv("accion", "ERROR_NO_CONTROLADO"),
                kv("resultado", "FALLIDO"),
                kv("ruta", request.getRequestURI()),
                ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", request, null);
    }

    private static String nombreDelParametro(MethodParameter parametro) {
        RequestParam requestParam = parametro.getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        RequestHeader requestHeader = parametro.getParameterAnnotation(RequestHeader.class);
        if (requestHeader != null && !requestHeader.name().isEmpty()) {
            return requestHeader.name();
        }
        return parametro.getParameterName();
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

    private static String mensajeDeCampo(FieldError error) {
        return error.isBindingFailure()
                ? "El tipo de dato no es válido"
                : Objects.requireNonNullElse(
                error.getDefaultMessage(), "El valor no es válido");
    }
}
