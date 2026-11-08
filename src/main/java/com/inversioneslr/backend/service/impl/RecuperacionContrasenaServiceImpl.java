package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.RecuperacionProperties;
import com.inversioneslr.backend.dto.request.RestablecerContrasenaRequest;
import com.inversioneslr.backend.dto.request.SolicitudRecuperacionRequest;
import com.inversioneslr.backend.dto.response.MensajeResponse;
import com.inversioneslr.backend.exception.TokenRecuperacionInvalidoException;
import com.inversioneslr.backend.model.SolicitudRecuperacion;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.repository.SolicitudRecuperacionRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.GeneradorTokenRecuperacion;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.RecuperacionContrasenaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class RecuperacionContrasenaServiceImpl implements RecuperacionContrasenaService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionContrasenaServiceImpl.class);

    static final String ENTIDAD_SOLICITUD = "SolicitudRecuperacion";
    static final String ENTIDAD_USUARIO = "Usuario";
    static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, recibirás las instrucciones para restablecer tu contraseña";
    static final String MENSAJE_RESTABLECIDA =
            "Tu contraseña se actualizó. Ya puedes iniciar sesión con la nueva contraseña";
    static final String MOTIVO_CORREO_NO_REGISTRADO = "CORREO_NO_REGISTRADO";
    static final String MOTIVO_CUENTA_INACTIVA = "CUENTA_INACTIVA";
    static final String MOTIVO_TOKEN_INEXISTENTE = "TOKEN_INEXISTENTE";
    static final String MOTIVO_TOKEN_USADO = "TOKEN_USADO";
    static final String MOTIVO_TOKEN_VENCIDO = "TOKEN_VENCIDO";

    private final UsuarioRepository usuarioRepository;
    private final SolicitudRecuperacionRepository solicitudRepository;
    private final GeneradorTokenRecuperacion generadorToken;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final RecuperacionProperties propiedades;
    private final Clock clock;

    public RecuperacionContrasenaServiceImpl(UsuarioRepository usuarioRepository,
                                             SolicitudRecuperacionRepository solicitudRepository,
                                             GeneradorTokenRecuperacion generadorToken,
                                             PasswordEncoder passwordEncoder, AuditoriaService auditoriaService,
                                             RecuperacionProperties propiedades, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.solicitudRepository = solicitudRepository;
        this.generadorToken = generadorToken;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.propiedades = propiedades;
        this.clock = clock;
    }

    @Override
    @Transactional
    public MensajeResponse solicitar(SolicitudRecuperacionRequest request) {
        String correo = request.correo().strip();
        Optional<Usuario> encontrado = usuarioRepository.findByCorreoIgnoreCase(correo);
        if (encontrado.isEmpty() || !encontrado.get().isCuentaActiva()) {
            // Se registra el intento, pero la respuesta es idéntica a la de un correo registrado.
            Long idUsuario = encontrado.map(Usuario::getIdUsuario).orElse(null);
            String motivo = encontrado.isEmpty() ? MOTIVO_CORREO_NO_REGISTRADO : MOTIVO_CUENTA_INACTIVA;
            auditoriaService.registrarIntentoFallido(idUsuario, correo, AccionAuditoria.SOLICITUD_RECUPERACION,
                    ENTIDAD_SOLICITUD, null, motivo);
            return new MensajeResponse(MENSAJE_SOLICITUD);
        }

        Usuario usuario = encontrado.get();
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        solicitudRepository.cerrarVigentes(usuario.getIdUsuario(), ahora);
        String token = generadorToken.generar();
        SolicitudRecuperacion solicitud = solicitudRepository.save(new SolicitudRecuperacion(usuario,
                generadorToken.hash(token), ahora, ahora.plusMinutes(propiedades.minutosValidez())));

        auditoriaService.registrarExito(usuario, AccionAuditoria.SOLICITUD_RECUPERACION, ENTIDAD_SOLICITUD,
                String.valueOf(solicitud.getIdSolicitud()));
        // Correo simulado: en lugar de enviarlo, el enlace queda en el log del servidor.
        log.info("Correo simulado de recuperación de contraseña {} {} {} {}",
                kv("accion", "CORREO_SIMULADO_RECUPERACION"),
                kv("destinatario", usuario.getCorreo()),
                kv("token", token),
                kv("venceEn", solicitud.getFechaExpiracion()));
        return new MensajeResponse(MENSAJE_SOLICITUD);
    }

    @Override
    @Transactional
    public MensajeResponse restablecer(RestablecerContrasenaRequest request) {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        SolicitudRecuperacion solicitud = solicitudRepository.findByTokenHash(generadorToken.hash(request.token()))
                .orElseThrow(() -> rechazar(null, null, MOTIVO_TOKEN_INEXISTENTE));
        Usuario usuario = solicitud.getUsuario();
        if (solicitud.estaUsada()) {
            throw rechazar(usuario, solicitud, MOTIVO_TOKEN_USADO);
        }
        if (solicitud.estaVencida(ahora)) {
            throw rechazar(usuario, solicitud, MOTIVO_TOKEN_VENCIDO);
        }
        if (!usuario.isCuentaActiva()) {
            throw rechazar(usuario, solicitud, MOTIVO_CUENTA_INACTIVA);
        }

        usuario.cambiarContrasenaHash(passwordEncoder.encode(request.contrasena()));
        solicitud.marcarUsada(ahora);
        usuarioRepository.save(usuario);
        solicitudRepository.save(solicitud);
        auditoriaService.registrarExito(usuario, AccionAuditoria.RECUPERACION_CONTRASENA, ENTIDAD_USUARIO,
                String.valueOf(usuario.getIdUsuario()));
        return new MensajeResponse(MENSAJE_RESTABLECIDA);
    }

    /**
     * Registra el intento rechazado y devuelve siempre el mismo error, sin decir por qué falló.
     */
    private TokenRecuperacionInvalidoException rechazar(Usuario usuario, SolicitudRecuperacion solicitud,
                                                        String motivo) {
        auditoriaService.registrarIntentoFallido(
                usuario == null ? null : usuario.getIdUsuario(),
                usuario == null ? null : usuario.getCorreo(),
                AccionAuditoria.RECUPERACION_CONTRASENA, ENTIDAD_SOLICITUD,
                solicitud == null ? null : String.valueOf(solicitud.getIdSolicitud()), motivo);
        return new TokenRecuperacionInvalidoException();
    }
}
