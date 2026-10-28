package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.LoginRequest;
import com.inversioneslr.backend.dto.response.LoginResponse;
import com.inversioneslr.backend.dto.response.SesionResponse;
import com.inversioneslr.backend.exception.CredencialesInvalidasException;
import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.TokenService;
import com.inversioneslr.backend.service.TokenService.TokenGenerado;
import com.inversioneslr.backend.service.TokenService.TokenValido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
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
class AutenticacionServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-10-28T12:00:00Z");
    private static final Instant EXPIRA = AHORA.plus(Duration.ofMinutes(60));
    private static final String HASH = "$2a$10$hashReal";
    private static final String HASH_SIMULADO = "$2a$10$hashSimulado";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private AuditoriaService auditoriaService;

    private AutenticacionServiceImpl servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(HASH_SIMULADO);
        servicio = new AutenticacionServiceImpl(usuarioRepository, passwordEncoder, tokenService, auditoriaService,
                new UsuarioMapper());
        usuario = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", HASH, Rol.ANFITRION);
        ReflectionTestUtils.setField(usuario, "idUsuario", 5L);
    }

    @Test
    @DisplayName("Criterio 1: con credenciales válidas autentica y crea la sesión (token)")
    void iniciarSesion_credencialesValidas_devuelveTokenYRegistraEvento() {
        when(usuarioRepository.findByCorreoIgnoreCase("carlos@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Segura#123", HASH)).thenReturn(true);
        when(tokenService.generar(usuario)).thenReturn(new TokenGenerado("token-jwt", AHORA, EXPIRA));

        LoginResponse respuesta = servicio.iniciarSesion(new LoginRequest(" Carlos@Correo.com ", "Segura#123"));

        assertThat(respuesta.token()).isEqualTo("token-jwt");
        assertThat(respuesta.tipo()).isEqualTo("Bearer");
        assertThat(respuesta.expiraEn()).isEqualTo(3600);
        assertThat(respuesta.usuario().rol()).isEqualTo(Rol.ANFITRION);
        verify(auditoriaService).registrarExito(usuario, AccionAuditoria.INICIO_SESION,
                AutenticacionServiceImpl.ENTIDAD_USUARIO, "5");
    }

    @Test
    @DisplayName("Criterio 2: contraseña incorrecta → mensaje genérico, sin token y con evento fallido")
    void iniciarSesion_contrasenaIncorrecta_rechazaSinToken() {
        when(usuarioRepository.findByCorreoIgnoreCase("carlos@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Otra#1234", HASH)).thenReturn(false);
        LoginRequest request = new LoginRequest("carlos@correo.com", "Otra#1234");

        assertThatThrownBy(() -> servicio.iniciarSesion(request))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Correo o contraseña incorrectos");

        verify(tokenService, never()).generar(any());
        verify(auditoriaService).registrarIntentoFallido(5L, "carlos@correo.com", AccionAuditoria.INICIO_SESION,
                AutenticacionServiceImpl.ENTIDAD_USUARIO, "5", AutenticacionServiceImpl.MOTIVO_CREDENCIALES_INVALIDAS);
    }

    @Test
    @DisplayName("Criterio 2: correo inexistente → mismo mensaje genérico, y se compara igual con BCrypt")
    void iniciarSesion_correoInexistente_mismoMensajeYComparaContraHashSimulado() {
        when(usuarioRepository.findByCorreoIgnoreCase("nadie@correo.com")).thenReturn(Optional.empty());
        LoginRequest request = new LoginRequest("nadie@correo.com", "Segura#123");

        assertThatThrownBy(() -> servicio.iniciarSesion(request))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Correo o contraseña incorrectos");

        verify(passwordEncoder).matches("Segura#123", HASH_SIMULADO);
        verify(auditoriaService).registrarIntentoFallido(isNull(), eq("nadie@correo.com"),
                eq(AccionAuditoria.INICIO_SESION), anyString(), isNull(),
                eq(AutenticacionServiceImpl.MOTIVO_CREDENCIALES_INVALIDAS));
    }

    @Test
    @DisplayName("Cuenta desactivada → mismo mensaje genérico y motivo en la auditoría")
    void iniciarSesion_cuentaDesactivada_rechazaConMensajeGenerico() {
        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        when(usuarioRepository.findByCorreoIgnoreCase("carlos@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Segura#123", HASH)).thenReturn(true);
        LoginRequest request = new LoginRequest("carlos@correo.com", "Segura#123");

        assertThatThrownBy(() -> servicio.iniciarSesion(request))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Correo o contraseña incorrectos");

        verify(tokenService, never()).generar(any());
        verify(auditoriaService).registrarIntentoFallido(eq(5L), anyString(), eq(AccionAuditoria.INICIO_SESION),
                anyString(), eq("5"), eq(AutenticacionServiceImpl.MOTIVO_CUENTA_DESACTIVADA));
    }

    @Test
    @DisplayName("Cuenta restringida → mismo mensaje genérico y motivo en la auditoría")
    void iniciarSesion_cuentaRestringida_rechazaConMensajeGenerico() {
        ReflectionTestUtils.setField(usuario, "restringido", true);
        when(usuarioRepository.findByCorreoIgnoreCase("carlos@correo.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Segura#123", HASH)).thenReturn(true);
        LoginRequest request = new LoginRequest("carlos@correo.com", "Segura#123");

        assertThatThrownBy(() -> servicio.iniciarSesion(request))
                .isInstanceOf(CredencialesInvalidasException.class);

        verify(auditoriaService).registrarIntentoFallido(eq(5L), anyString(), eq(AccionAuditoria.INICIO_SESION),
                anyString(), eq("5"), eq(AutenticacionServiceImpl.MOTIVO_CUENTA_RESTRINGIDA));
    }

    @Test
    void autenticarToken_valido_devuelveUsuarioConRolDeLaBaseDeDatos() {
        when(tokenService.validar("token")).thenReturn(Optional.of(new TokenValido(5L, EXPIRA)));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        assertThat(servicio.autenticarToken("token"))
                .contains(new UsuarioAutenticado(5L, "carlos@correo.com", Rol.ANFITRION, EXPIRA));
    }

    @Test
    void autenticarToken_invalido_devuelveVacio() {
        when(tokenService.validar("malo")).thenReturn(Optional.empty());

        assertThat(servicio.autenticarToken("malo")).isEmpty();
        verify(usuarioRepository, never()).findById(any());
    }

    @Test
    void autenticarToken_usuarioYaNoExiste_devuelveVacio() {
        when(tokenService.validar("token")).thenReturn(Optional.of(new TokenValido(5L, EXPIRA)));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.empty());

        assertThat(servicio.autenticarToken("token")).isEmpty();
    }

    @Test
    @DisplayName("Si la cuenta se desactiva o restringe después del login, el token deja de servir")
    void autenticarToken_cuentaDesactivadaORestringida_devuelveVacio() {
        when(tokenService.validar("token")).thenReturn(Optional.of(new TokenValido(5L, EXPIRA)));
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        assertThat(servicio.autenticarToken("token")).isEmpty();

        ReflectionTestUtils.setField(usuario, "cuentaActiva", true);
        ReflectionTestUtils.setField(usuario, "restringido", true);
        assertThat(servicio.autenticarToken("token")).isEmpty();
    }

    @Test
    void obtenerSesion_devuelveUsuarioYExpiracion() {
        when(usuarioRepository.findById(5L)).thenReturn(Optional.of(usuario));

        SesionResponse sesion = servicio.obtenerSesion(
                new UsuarioAutenticado(5L, "carlos@correo.com", Rol.ANFITRION, EXPIRA));

        assertThat(sesion.usuario().idUsuario()).isEqualTo(5L);
        assertThat(sesion.usuario().rol()).isEqualTo(Rol.ANFITRION);
        assertThat(sesion.expiraEn()).isEqualTo(EXPIRA);
    }
}
