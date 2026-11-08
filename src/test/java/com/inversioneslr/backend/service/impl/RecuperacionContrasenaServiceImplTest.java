package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.RecuperacionProperties;
import com.inversioneslr.backend.dto.request.RestablecerContrasenaRequest;
import com.inversioneslr.backend.dto.request.SolicitudRecuperacionRequest;
import com.inversioneslr.backend.dto.response.MensajeResponse;
import com.inversioneslr.backend.exception.TokenRecuperacionInvalidoException;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.SolicitudRecuperacion;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.SolicitudRecuperacionRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.GeneradorTokenRecuperacion;
import com.inversioneslr.backend.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecuperacionContrasenaServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");
    private static final OffsetDateTime AHORA_UTC = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final String TOKEN = "token-de-prueba";
    private static final String TOKEN_HASH = "hash-del-token";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SolicitudRecuperacionRepository solicitudRepository;
    @Mock
    private GeneradorTokenRecuperacion generadorToken;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;

    private RecuperacionContrasenaServiceImpl servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        servicio = new RecuperacionContrasenaServiceImpl(usuarioRepository, solicitudRepository, generadorToken,
                passwordEncoder, auditoriaService, new RecuperacionProperties(30), Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash-viejo",
                Rol.HUESPED);
        ReflectionTestUtils.setField(usuario, "idUsuario", 7L);
    }

    @Test
    @DisplayName("Criterio 1: con un correo registrado se registra la solicitud con vencimiento a 30 minutos")
    void solicitar_correoRegistrado_registraSolicitud() {
        when(usuarioRepository.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(generadorToken.generar()).thenReturn(TOKEN);
        when(generadorToken.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(solicitudRepository.save(any(SolicitudRecuperacion.class))).thenAnswer(inv -> {
            SolicitudRecuperacion solicitud = inv.getArgument(0);
            ReflectionTestUtils.setField(solicitud, "idSolicitud", 3L);
            return solicitud;
        });

        MensajeResponse respuesta = servicio.solicitar(new SolicitudRecuperacionRequest(" ana@correo.com "));

        assertThat(respuesta.mensaje()).isEqualTo(RecuperacionContrasenaServiceImpl.MENSAJE_SOLICITUD);
        ArgumentCaptor<SolicitudRecuperacion> captor = ArgumentCaptor.forClass(SolicitudRecuperacion.class);
        verify(solicitudRepository).save(captor.capture());
        SolicitudRecuperacion guardada = captor.getValue();
        assertThat(guardada.getUsuario()).isSameAs(usuario);
        assertThat(guardada.getTokenHash()).isEqualTo(TOKEN_HASH);
        assertThat(guardada.getFechaSolicitud()).isEqualTo(AHORA_UTC);
        assertThat(guardada.getFechaExpiracion()).isEqualTo(AHORA_UTC.plusMinutes(30));
        assertThat(guardada.getFechaUso()).isNull();
    }

    @Test
    @DisplayName("Una solicitud nueva cierra las anteriores del usuario")
    void solicitar_correoRegistrado_cierraSolicitudesAnteriores() {
        prepararSolicitudExitosa();

        servicio.solicitar(new SolicitudRecuperacionRequest("ana@correo.com"));

        verify(solicitudRepository).cerrarVigentes(7L, AHORA_UTC);
    }

    @Test
    @DisplayName("Criterio 5: la solicitud queda registrada para trazabilidad")
    void solicitar_correoRegistrado_registraEvento() {
        prepararSolicitudExitosa();

        servicio.solicitar(new SolicitudRecuperacionRequest("ana@correo.com"));

        verify(auditoriaService).registrarExito(usuario, AccionAuditoria.SOLICITUD_RECUPERACION,
                RecuperacionContrasenaServiceImpl.ENTIDAD_SOLICITUD, "3");
    }

    @Test
    @DisplayName("Criterio 2: con un correo no registrado responde igual y no genera token")
    void solicitar_correoNoRegistrado_respondeIgualSinGenerarToken() {
        when(usuarioRepository.findByCorreoIgnoreCase("nadie@correo.com")).thenReturn(Optional.empty());

        MensajeResponse respuesta = servicio.solicitar(new SolicitudRecuperacionRequest("nadie@correo.com"));

        assertThat(respuesta.mensaje()).isEqualTo(RecuperacionContrasenaServiceImpl.MENSAJE_SOLICITUD);
        verify(generadorToken, never()).generar();
        verify(solicitudRepository, never()).save(any());
        verify(auditoriaService).registrarIntentoFallido(null, "nadie@correo.com",
                AccionAuditoria.SOLICITUD_RECUPERACION, RecuperacionContrasenaServiceImpl.ENTIDAD_SOLICITUD, null,
                RecuperacionContrasenaServiceImpl.MOTIVO_CORREO_NO_REGISTRADO);
    }

    @Test
    void solicitar_cuentaInactiva_respondeIgualSinGenerarToken() {
        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        when(usuarioRepository.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(usuario));

        MensajeResponse respuesta = servicio.solicitar(new SolicitudRecuperacionRequest("ana@correo.com"));

        assertThat(respuesta.mensaje()).isEqualTo(RecuperacionContrasenaServiceImpl.MENSAJE_SOLICITUD);
        verify(solicitudRepository, never()).save(any());
        verify(auditoriaService).registrarIntentoFallido(7L, "ana@correo.com", AccionAuditoria.SOLICITUD_RECUPERACION,
                RecuperacionContrasenaServiceImpl.ENTIDAD_SOLICITUD, null,
                RecuperacionContrasenaServiceImpl.MOTIVO_CUENTA_INACTIVA);
    }

    @Test
    @DisplayName("Criterio 3: con un token vigente se guarda la nueva contraseña cifrada y el token queda usado")
    void restablecer_tokenVigente_cambiaContrasenaYUsaToken() {
        SolicitudRecuperacion solicitud = solicitud(AHORA_UTC.plusMinutes(10));
        when(generadorToken.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(solicitudRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(solicitud));
        when(passwordEncoder.encode("Nueva#456")).thenReturn("hash-nuevo");

        MensajeResponse respuesta = servicio.restablecer(request(TOKEN));

        assertThat(respuesta.mensaje()).isEqualTo(RecuperacionContrasenaServiceImpl.MENSAJE_RESTABLECIDA);
        assertThat(usuario.getContrasenaHash()).isEqualTo("hash-nuevo");
        assertThat(solicitud.getFechaUso()).isEqualTo(AHORA_UTC);
        verify(usuarioRepository).save(usuario);
        verify(auditoriaService).registrarExito(usuario, AccionAuditoria.RECUPERACION_CONTRASENA,
                RecuperacionContrasenaServiceImpl.ENTIDAD_USUARIO, "7");
    }

    @Test
    void restablecer_tokenInexistente_rechazaSinCambios() {
        when(generadorToken.hash("otro")).thenReturn("hash-otro");
        when(solicitudRepository.findByTokenHash("hash-otro")).thenReturn(Optional.empty());
        RestablecerContrasenaRequest request = request("otro");

        assertThatThrownBy(() -> servicio.restablecer(request))
                .isInstanceOf(TokenRecuperacionInvalidoException.class)
                .hasMessage("El enlace de recuperación no es válido o ha expirado");

        verify(passwordEncoder, never()).encode(anyString());
        verify(auditoriaService).registrarIntentoFallido(isNull(), isNull(),
                eq(AccionAuditoria.RECUPERACION_CONTRASENA), eq(RecuperacionContrasenaServiceImpl.ENTIDAD_SOLICITUD),
                isNull(), eq(RecuperacionContrasenaServiceImpl.MOTIVO_TOKEN_INEXISTENTE));
    }

    @Test
    void restablecer_tokenUsado_rechazaSinCambios() {
        SolicitudRecuperacion solicitud = solicitud(AHORA_UTC.plusMinutes(10));
        solicitud.marcarUsada(AHORA_UTC.minusMinutes(1));
        prepararBusqueda(solicitud);
        RestablecerContrasenaRequest request = request(TOKEN);

        assertThatThrownBy(() -> servicio.restablecer(request)).isInstanceOf(TokenRecuperacionInvalidoException.class);

        assertThat(usuario.getContrasenaHash()).isEqualTo("hash-viejo");
        verify(auditoriaService).registrarIntentoFallido(7L, "ana@correo.com",
                AccionAuditoria.RECUPERACION_CONTRASENA, RecuperacionContrasenaServiceImpl.ENTIDAD_SOLICITUD, "3",
                RecuperacionContrasenaServiceImpl.MOTIVO_TOKEN_USADO);
    }

    @Test
    void restablecer_tokenVencido_rechazaSinCambios() {
        prepararBusqueda(solicitud(AHORA_UTC));
        RestablecerContrasenaRequest request = request(TOKEN);

        assertThatThrownBy(() -> servicio.restablecer(request)).isInstanceOf(TokenRecuperacionInvalidoException.class);

        assertThat(usuario.getContrasenaHash()).isEqualTo("hash-viejo");
        verify(auditoriaService).registrarIntentoFallido(7L, "ana@correo.com",
                AccionAuditoria.RECUPERACION_CONTRASENA, RecuperacionContrasenaServiceImpl.ENTIDAD_SOLICITUD, "3",
                RecuperacionContrasenaServiceImpl.MOTIVO_TOKEN_VENCIDO);
    }

    @Test
    void restablecer_cuentaInactiva_rechazaSinCambios() {
        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        prepararBusqueda(solicitud(AHORA_UTC.plusMinutes(10)));
        RestablecerContrasenaRequest request = request(TOKEN);

        assertThatThrownBy(() -> servicio.restablecer(request)).isInstanceOf(TokenRecuperacionInvalidoException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    private void prepararSolicitudExitosa() {
        when(usuarioRepository.findByCorreoIgnoreCase("ana@correo.com")).thenReturn(Optional.of(usuario));
        when(generadorToken.generar()).thenReturn(TOKEN);
        when(generadorToken.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(solicitudRepository.save(any(SolicitudRecuperacion.class))).thenAnswer(inv -> {
            SolicitudRecuperacion solicitud = inv.getArgument(0);
            ReflectionTestUtils.setField(solicitud, "idSolicitud", 3L);
            return solicitud;
        });
    }

    private void prepararBusqueda(SolicitudRecuperacion solicitud) {
        when(generadorToken.hash(TOKEN)).thenReturn(TOKEN_HASH);
        when(solicitudRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(solicitud));
    }

    private SolicitudRecuperacion solicitud(OffsetDateTime vence) {
        SolicitudRecuperacion solicitud =
                new SolicitudRecuperacion(usuario, TOKEN_HASH, AHORA_UTC.minusMinutes(20), vence);
        ReflectionTestUtils.setField(solicitud, "idSolicitud", 3L);
        return solicitud;
    }

    private static RestablecerContrasenaRequest request(String token) {
        return new RestablecerContrasenaRequest(token, "Nueva#456", "Nueva#456");
    }
}
