package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.AdminInicialProperties;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministradorInicialServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void crearSiNoExiste_sinAdministrador_loCreaConContrasenaCifrada() {
        when(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("admin@correo.com")).thenReturn(false);
        when(passwordEncoder.encode("Clave#2026")).thenReturn("$2a$10$hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean creado = servicio(" Admin@Correo.com ", "Clave#2026").crearSiNoExiste();

        assertThat(creado).isTrue();
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario administrador = captor.getValue();
        assertThat(administrador.getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(administrador.getCorreo()).isEqualTo("admin@correo.com");
        assertThat(administrador.getContrasenaHash()).isEqualTo("$2a$10$hash");
        assertThat(administrador.getNombre()).isEqualTo(AdministradorInicialServiceImpl.NOMBRE_ADMINISTRADOR);
    }

    @Test
    void crearSiNoExiste_yaHayAdministrador_noCreaOtro() {
        when(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).thenReturn(true);

        assertThat(servicio("admin@correo.com", "Clave#2026").crearSiNoExiste()).isFalse();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearSiNoExiste_sinVariablesDeEntorno_noCreaNada() {
        when(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).thenReturn(false);

        assertThat(servicio("", null).crearSiNoExiste()).isFalse();
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearSiNoExiste_correoUsadoPorOtraCuenta_noCreaNada() {
        when(usuarioRepository.existsByRol(Rol.ADMINISTRADOR)).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("admin@correo.com")).thenReturn(true);

        assertThat(servicio("admin@correo.com", "Clave#2026").crearSiNoExiste()).isFalse();
        verify(usuarioRepository, never()).save(any());
    }

    private AdministradorInicialServiceImpl servicio(String correo, String contrasena) {
        return new AdministradorInicialServiceImpl(usuarioRepository, passwordEncoder,
                new AdminInicialProperties(correo, contrasena));
    }
}
