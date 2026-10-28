package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.AdministradorInicialService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Administrador se carga como dato inicial al arrancar, con las variables de entorno.
 */
@SpringBootTest(properties = {
        "seguridad.admin-inicial.correo=admin.inicial@correo.com",
        "seguridad.admin-inicial.contrasena=Clave#Admin1"
})
@Import(TestcontainersConfiguration.class)
class AdministradorInicialIntegrationTest {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdministradorInicialService administradorInicialService;

    @Autowired
    AdministradorInicialIntegrationTest(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                                        AdministradorInicialService administradorInicialService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.administradorInicialService = administradorInicialService;
    }

    @Test
    void alArrancar_creaElAdministradorUnaSolaVez() {
        var administrador = usuarioRepository.findByCorreoIgnoreCase("admin.inicial@correo.com").orElseThrow();
        assertThat(administrador.getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(passwordEncoder.matches("Clave#Admin1", administrador.getContrasenaHash())).isTrue();

        // Volver a ejecutarlo (por ejemplo, en el siguiente arranque) no crea otro.
        assertThat(administradorInicialService.crearSiNoExiste()).isFalse();
        assertThat(usuarioRepository.findAll().stream().filter(u -> u.getRol() == Rol.ADMINISTRADOR)).hasSize(1);
    }
}
