package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class UsuarioRepositoryIntegrationTest {

    private final UsuarioRepository usuarioRepository;

    @Autowired
    UsuarioRepositoryIntegrationTest(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Test
    void guardar_asignaIdYValoresPorDefecto() {
        Usuario guardado = usuarioRepository.saveAndFlush(
                new Usuario("Ana", "ana@correo.com", "hash", Rol.HUESPED));

        assertThat(guardado.getIdUsuario()).isNotNull();
        assertThat(guardado.isCuentaActiva()).isTrue();
        assertThat(guardado.isRestringido()).isFalse();
    }

    @Test
    void existsByCorreoIgnoreCase_encuentraSinDistinguirMayusculas() {
        usuarioRepository.saveAndFlush(new Usuario("Ana", "ana@correo.com", "hash", Rol.HUESPED));

        assertThat(usuarioRepository.existsByCorreoIgnoreCase("ANA@Correo.com")).isTrue();
        assertThat(usuarioRepository.existsByCorreoIgnoreCase("otra@correo.com")).isFalse();
    }

    @Test
    void indiceUnico_rechazaElMismoCorreoConOtrasMayusculas() {
        usuarioRepository.saveAndFlush(new Usuario("Ana", "ana@correo.com", "hash", Rol.HUESPED));
        Usuario duplicado = new Usuario("Ana 2", "ANA@CORREO.COM", "hash", Rol.HUESPED);

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_usuario_correo");
    }
}
