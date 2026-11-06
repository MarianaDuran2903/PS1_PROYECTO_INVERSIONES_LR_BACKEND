package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.mapper.UsuarioMapper;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private PerfilServiceImpl servicio;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        servicio = new PerfilServiceImpl(
                usuarioRepository, new UsuarioMapper());

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
}