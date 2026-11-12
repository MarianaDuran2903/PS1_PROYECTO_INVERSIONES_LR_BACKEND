package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.dto.request.ActualizarEstadoCuentaRequest;
import com.inversioneslr.backend.exception.EstadoCuentaInvalidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import com.inversioneslr.backend.dto.request.ActualizarPerfilRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.service.AuditoriaService;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private PerfilServiceImpl servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        servicio = new PerfilServiceImpl(usuarioRepository, new UsuarioMapper(), auditoriaService);

        usuario = new Usuario(
                new NombreCompleto("Ana", "María", "Ruiz", null),
                "ana@example.com",
                "hash-no-expuesto",
                Rol.HUESPED);

        ReflectionTestUtils.setField(usuario, "idUsuario", 7L);
    }

    @Test
    void consultarPerfil_devuelveDatosActualesDeLaCuenta() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        var respuesta = servicio.consultarPerfil(7L);

        assertThat(respuesta.idUsuario()).isEqualTo(7L);
        assertThat(respuesta.primerNombre()).isEqualTo("Ana");
        assertThat(respuesta.segundoNombre()).isEqualTo("María");
        assertThat(respuesta.primerApellido()).isEqualTo("Ruiz");
        assertThat(respuesta.segundoApellido()).isNull();
        assertThat(respuesta.correo()).isEqualTo("ana@example.com");
        assertThat(respuesta.rol()).isEqualTo(Rol.HUESPED);
        assertThat(respuesta.cuentaActiva()).isTrue();

        verify(usuarioRepository).findById(7L);
    }

    @Test
    void consultarPerfil_usuarioInexistente_rechaza() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultarPerfil(7L))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void consultarPerfil_cuentaInactiva_rechaza() {
        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servicio.consultarPerfil(7L))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void consultarPerfil_cuentaRestringida_rechaza() {
        ReflectionTestUtils.setField(usuario, "restringido", true);
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servicio.consultarPerfil(7L))
                .isInstanceOf(BadCredentialsException.class);
    }

    private ActualizarPerfilRequest solicitudValida() {
        return new ActualizarPerfilRequest(
                "Carlos",
                null,
                "Gomez",
                "Nuevo"
        );
    }

    @Test
    void actualizarPerfil_modificaSoloCamposPermitidos() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        UsuarioResponse respuesta = servicio.actualizarPerfil(7L, solicitudValida());

        assertThat(respuesta.primerNombre()).isEqualTo("Carlos");
        assertThat(respuesta.segundoNombre()).isNull();
        assertThat(respuesta.primerApellido()).isEqualTo("Gomez");
        assertThat(respuesta.segundoApellido()).isEqualTo("Nuevo");

        assertThat(respuesta.correo()).isEqualTo("ana@example.com");
        assertThat(respuesta.rol()).isEqualTo(Rol.HUESPED);
        assertThat(respuesta.cuentaActiva()).isTrue();

        verify(auditoriaService).registrarExito(
                usuario,
                AccionAuditoria.ACTUALIZACION_PERFIL,
                "Usuario",
                "7"
        );
    }

    @Test
    void actualizarPerfil_normalizaEspaciosYVaciaOpcionales() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        ActualizarPerfilRequest request = new ActualizarPerfilRequest(
                "  Carlos  ",
                "   ",
                " Gomez ",
                ""
        );

        servicio.actualizarPerfil(7L, request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());

        NombreCompleto nombre = captor.getValue().getNombre();
        assertThat(nombre.primerNombre()).isEqualTo("Carlos");
        assertThat(nombre.segundoNombre()).isNull();
        assertThat(nombre.primerApellido()).isEqualTo("Gomez");
        assertThat(nombre.segundoApellido()).isNull();
    }

    @Test
    void actualizarPerfil_usuarioInexistente_rechaza() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicio.actualizarPerfil(7L, solicitudValida())
        ).isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(
                any(), any(), any(), any()
        );
    }

    @Test
    void actualizarPerfil_cuentaInactiva_rechaza() {
        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() ->
                servicio.actualizarPerfil(7L, solicitudValida())
        ).isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void actualizarPerfil_cuentaRestringida_rechaza() {
        ReflectionTestUtils.setField(usuario, "restringido", true);
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() ->
                servicio.actualizarPerfil(7L, solicitudValida())
        ).isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).save(any());
    }

    private ActualizarEstadoCuentaRequest desactivacion() {
        return new ActualizarEstadoCuentaRequest(false);
    }

    @Test
    void cambiarEstado_false_desactivaCuentaYRegistraAuditoria() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        UsuarioResponse respuesta = servicio.cambiarEstado(7L, desactivacion());

        assertThat(respuesta.idUsuario()).isEqualTo(7L);
        assertThat(respuesta.correo()).isEqualTo("ana@example.com");
        assertThat(respuesta.rol()).isEqualTo(Rol.HUESPED);
        assertThat(respuesta.cuentaActiva()).isFalse();

        assertThat(usuario.isCuentaActiva()).isFalse();

        verify(auditoriaService).registrarExito(
                usuario,
                AccionAuditoria.DESACTIVACION_CUENTA,
                "Usuario",
                "7"
        );
    }

    @Test
    void cambiarEstado_true_rechazaSinConsultarNiGuardar() {
        ActualizarEstadoCuentaRequest request =
                new ActualizarEstadoCuentaRequest(true);

        assertThatThrownBy(() -> servicio.cambiarEstado(7L, request))
                .isInstanceOf(EstadoCuentaInvalidoException.class)
                .hasMessage("La reactivación requiere iniciar sesión con tus credenciales.");

        verify(usuarioRepository, never()).findById(any());
        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(
                any(), any(), any(), any()
        );
    }

    @Test
    void cambiarEstado_usuarioInexistente_rechaza() {
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarEstado(7L, desactivacion()))
                .isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(
                any(), any(), any(), any()
        );
    }

    @Test
    void cambiarEstado_cuentaInactiva_rechaza() {
        ReflectionTestUtils.setField(usuario, "cuentaActiva", false);
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servicio.cambiarEstado(7L, desactivacion()))
                .isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(
                any(), any(), any(), any()
        );
    }

    @Test
    void cambiarEstado_cuentaRestringida_rechaza() {
        ReflectionTestUtils.setField(usuario, "restringido", true);
        when(usuarioRepository.findById(7L))
                .thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> servicio.cambiarEstado(7L, desactivacion()))
                .isInstanceOf(BadCredentialsException.class);

        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(
                any(), any(), any(), any()
        );
    }
}