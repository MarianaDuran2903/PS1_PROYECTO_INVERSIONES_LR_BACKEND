package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.RegistroHuespedRequest;
import com.inversioneslr.backend.dto.response.UsuarioResponse;
import com.inversioneslr.backend.exception.CorreoYaRegistradoException;
import com.inversioneslr.backend.mapper.UsuarioMapper;
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
    @DisplayName("Dado un visitante con datos válidos, cuando se registra, entonces se crea la cuenta con rol Huésped")
    void registrarHuesped_datosValidos_creaCuentaConRolHuesped() {
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@correo.com")).thenReturn(false);
        when(passwordEncoder.encode("Segura123")).thenReturn(HASH);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> conId(inv.getArgument(0), 1L));

        UsuarioResponse respuesta = servicio.registrarHuesped(
                new RegistroHuespedRequest("Ana Pérez", "ana@correo.com", "Segura123"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).saveAndFlush(captor.capture());
        Usuario guardado = captor.getValue();
        assertThat(guardado.getRol()).isEqualTo(Rol.HUESPED);
        assertThat(guardado.getContrasenaHash()).isEqualTo(HASH);
        assertThat(guardado.isCuentaActiva()).isTrue();
        assertThat(guardado.isRestringido()).isFalse();

        assertThat(respuesta.idUsuario()).isEqualTo(1L);
        assertThat(respuesta.rol()).isEqualTo(Rol.HUESPED);
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
        verify(auditoriaService).registrarExito(guardado, AccionAuditoria.REGISTRO_USUARIO,
                RegistroUsuarioServiceImpl.ENTIDAD_USUARIO, "1");
    }

    @Test
    @DisplayName("El correo se guarda en minúsculas y sin espacios, y el nombre sin espacios sobrantes")
    void registrarHuesped_normalizaCorreoYNombre() {
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@correo.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> conId(inv.getArgument(0), 2L));

        UsuarioResponse respuesta = servicio.registrarHuesped(
                new RegistroHuespedRequest("  Ana Pérez ", "  Ana@Correo.COM ", "Segura123"));

        assertThat(respuesta.correo()).isEqualTo("ana@correo.com");
        assertThat(respuesta.nombre()).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("Dado un correo ya registrado, cuando se registra, entonces se rechaza y no se guarda nada")
    void registrarHuesped_correoDuplicado_lanzaExcepcion() {
        when(usuarioRepository.existsByCorreoIgnoreCase("ana@correo.com")).thenReturn(true);

        RegistroHuespedRequest request = new RegistroHuespedRequest("Ana", "ana@correo.com", "Segura123");
        assertThatThrownBy(() -> servicio.registrarHuesped(request))
                .isInstanceOf(CorreoYaRegistradoException.class);

        verify(usuarioRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(auditoriaService).registrarFallo("ana@correo.com", AccionAuditoria.REGISTRO_USUARIO,
                RegistroUsuarioServiceImpl.ENTIDAD_USUARIO, RegistroUsuarioServiceImpl.MOTIVO_CORREO_DUPLICADO);
    }

    @Test
    @DisplayName("Si dos registros simultáneos usan el mismo correo, el índice único se traduce a correo duplicado")
    void registrarHuesped_violacionIndiceUnico_lanzaCorreoYaRegistrado() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException(
                "duplicado", new SQLException("duplicate key value violates unique constraint \"uq_usuario_correo\"")));

        RegistroHuespedRequest request = new RegistroHuespedRequest("Ana", "ana@correo.com", "Segura123");
        assertThatThrownBy(() -> servicio.registrarHuesped(request))
                .isInstanceOf(CorreoYaRegistradoException.class);

        verify(auditoriaService).registrarFallo(eq("ana@correo.com"), eq(AccionAuditoria.REGISTRO_USUARIO),
                anyString(), eq(RegistroUsuarioServiceImpl.MOTIVO_CORREO_DUPLICADO));
        verify(auditoriaService, never()).registrarExito(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Otras violaciones de integridad no se confunden con correo duplicado")
    void registrarHuesped_otraViolacion_seRelanza() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(HASH);
        DataIntegrityViolationException otra = new DataIntegrityViolationException(
                "otra", new SQLException("violates check constraint \"ck_usuario_rol\""));
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenThrow(otra);

        RegistroHuespedRequest request = new RegistroHuespedRequest("Ana", "ana@correo.com", "Segura123");
        assertThatThrownBy(() -> servicio.registrarHuesped(request)).isSameAs(otra);
    }

    private static Usuario conId(Usuario usuario, Long id) {
        ReflectionTestUtils.setField(usuario, "idUsuario", id);
        return usuario;
    }
}
