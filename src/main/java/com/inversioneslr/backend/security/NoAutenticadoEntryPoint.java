package com.inversioneslr.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Responde 401 con el formato estándar de error cuando una ruta protegida se pide sin token válido
 * (RNF-SEG-002).
 */
@Component
public class NoAutenticadoEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(NoAutenticadoEntryPoint.class);
    static final String MENSAJE = "Debe iniciar sesión";

    private final ObjectMapper objectMapper;

    public NoAutenticadoEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("Acceso sin autenticar {} {}",
                kv("accion", "ACCESO_SIN_AUTENTICAR"),
                kv("resultado", "FALLIDO"),
                kv("ruta", request.getMethod() + " " + request.getRequestURI()));

        HttpStatus estado = HttpStatus.UNAUTHORIZED;
        ErrorResponse cuerpo = new ErrorResponse(OffsetDateTime.now(), estado.value(), estado.getReasonPhrase(),
                MENSAJE, request.getRequestURI(), null);
        response.setStatus(estado.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), cuerpo);
    }
}
