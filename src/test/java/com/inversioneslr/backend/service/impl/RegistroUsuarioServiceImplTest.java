package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.RegistroUsuarioRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.exception.CorreoYaRegistradoException;
import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistroUsuarioServiceImplTest {

    private static final String HASH = "$2a$10$hashDePrueba";
    private static final String CONTRASENA = "Segura#123";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditoriaService auditoriaService;

    private RegistroUsuarioServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new RegistroUsuarioServiceImpl(usuarioRepository, passwordEncoder, auditoriaService,
                new UsuarioMapper());
    }

    @Test
    @DisplayName("HU-001: dado un visitante con datos válidos, cuando se registra como Huésped, entonces la cuenta queda con rol Huésped")
    void registrarHuesped_datosValidos_creaCuentaConRolHuesped() {
        prepararRegistroExitoso("ana@correo.com", 1L);

        UsuarioResponse respuesta = servicio.registrarHuesped(request("ana@correo.com"));

        Usuario guardado = usuarioGuardado();
        assertThat(guardado.getRol()).isEqualTo(Rol.HUESPED);
        assertThat(guardado.getContrasenaHash()).isEqualTo(HASH);
        assertThat(guardado.isCuentaActiva()).isTrue();
        assertThat(guardado.isRestringido()).isFalse();
        assertThat(respuesta.rol()).isEqualTo(Rol.HUESPED);
        assertThat(respuesta.idUsuario()).isEqualTo(1L);
    }

    @Test
    @DisplayName("HU-002 criterio 1: dado un visitante con datos válidos, cuando se registra como Anfitrión, entonces la cuenta queda con rol Anfitrión")
    void registrarAnfitrion_datosValidos_creaCuentaConRolAnfitrion() {
        prepararRegistroExitoso("carlos@correo.com", 5L);

        UsuarioResponse respuesta = servicio.registrarAnfitrion(request("carlos@correo.com"));

        Usuario guardado = usuarioGuardado();
        assertThat(guardado.getRol()).isEqualTo(Rol.ANFITRION);
        assertThat(guardado.getContrasenaHash()).isEqualTo(HASH);
        assertThat(respuesta.rol()).isEqualTo(Rol.ANFITRION);
        assertThat(respuesta.primerNombre()).isEqualTo("Carlos");
        assertThat(respuesta.primerApellido()).isEqualTo("Gómez");
    }

    @Test
    @DisplayName("HU-002 criterio 4: un registro exitoso deja el evento de trazabilidad")
    void registrarAnfitrion_exitoso_registraEventoDeAuditoria() {
        prepararRegistroExitoso("carlos@correo.com", 5L);

        servicio.registrarAnfitrion(request("carlos@correo.com"));

        Usuario guardado = usuarioGuardado();
        verify(auditoriaService).registrarExito(guardado, AccionAuditoria.REGISTRO_USUARIO,
                RegistroUsuarioServiceImpl.ENTIDAD_USUARIO, "5");
    }

    @Test
    @DisplayName("Normaliza el correo y los nombres; los opcionales vacíos quedan nulos")
    void registrar_normalizaCorreoYNombres() {
        prepararRegistroExitoso("ana@correo.com", 2L);
        RegistroUsuarioRequest request = new RegistroUsuarioRequest(
                "  Ana ", "   ", " Pérez ", null, "  Ana@Correo.COM ", CONTRASENA, CONTRASENA);

        UsuarioResponse respuesta = servicio.registrarHuesped(request);

        assertThat(respuesta.correo()).isEqualTo("ana@correo.com");
        assertThat(respuesta.primerNombre()).isEqualTo("Ana");
        assertThat(respuesta.segundoNombre()).isNull();
        assertThat(respuesta.primerApellido()).isEqualTo("Pérez");
        assertThat(respuesta.segundoApellido()).isNull();
    }

    @Test
    @DisplayName("Guarda los cuatro nombres cuando se envían")
    void registrar_conSegundoNombreYApellido_losGuarda() {
        prepararRegistroExitoso("maria@correo.com", 3L);
        RegistroUsuarioRequest request = new RegistroUsuarioRequest(
                "María", "José", "Rodríguez", "O'Neill", "maria@correo.com", CONTRASENA, CONTRASENA);

        servicio.registrarAnfitrion(request);

        assertThat(usuarioGuardado().getNombre())
                .isEqualTo(new NombreCompleto("María", "José", "Rodríguez", "O'Neill"));
    }

    @Test
    @DisplayName("HU-002 criterio 2: dado un correo ya registrado, cuando se registra, entonces se rechaza y no se crea la cuenta")
    void registrarAnfitrion_correoDuplicado_lanzaExcepcionSinGuardar() {
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@correo.com")).thenReturn(true);
        RegistroUsuarioRequest request = request("ana@correo.com");

        assertThatThrownBy(() -> servicio.registrarAnfitrion(request))
                .isInstanceOf(CorreoYaRegistradoException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(auditoriaService).registrarFallo("ana@correo.com", AccionAuditoria.REGISTRO_USUARIO,
                RegistroUsuarioServiceImpl.ENTIDAD_USUARIO, RegistroUsuarioServiceImpl.MOTIVO_CORREO_DUPLICADO);
        verify(auditoriaService, never()).registrarExito(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Si dos registros simultáneos usan el mismo correo, el índice único se traduce a correo duplicado")
    void registrar_violacionIndiceUnico_lanzaCorreoYaRegistrado() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException(
                "duplicado", new SQLException("duplicate key value violates unique constraint \"uq_usuario_correo\"")));
        RegistroUsuarioRequest request = request("ana@correo.com");

        assertThatThrownBy(() -> servicio.registrarHuesped(request))
                .isInstanceOf(CorreoYaRegistradoException.class);

        verify(auditoriaService).registrarFallo(eq("ana@correo.com"), eq(AccionAuditoria.REGISTRO_USUARIO),
                anyString(), eq(RegistroUsuarioServiceImpl.MOTIVO_CORREO_DUPLICADO));
    }

    @Test
    @DisplayName("Otras violaciones de integridad no se confunden con correo duplicado")
    void registrar_otraViolacion_seRelanza() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        DataIntegrityViolationException otra = new DataIntegrityViolationException(
                "otra", new SQLException("violates check constraint \"ck_usuario_rol\""));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenThrow(otra);
        RegistroUsuarioRequest request = request("ana@correo.com");

        assertThatThrownBy(() -> servicio.registrarAnfitrion(request)).isSameAs(otra);
    }

    private void prepararRegistroExitoso(String correo, Long id) {
        when(usuarioRepository.existsByCorreoIgnoreCase(correo)).thenReturn(false);
        when(passwordEncoder.encode(CONTRASENA)).thenReturn(HASH);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario usuario = inv.getArgument(0);
            ReflectionTestUtils.setField(usuario, "idUsuario", id);
            return usuario;
        });
    }

    private Usuario usuarioGuardado() {
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static RegistroUsuarioRequest request(String correo) {
        return new RegistroUsuarioRequest("Carlos", null, "Gómez", null, correo, CONTRASENA, CONTRASENA);
    }
}
