package com.inversioneslr.backend.repository;

import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import jakarta.persistence.EntityManager;
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

    private static final NombreCompleto ANA = new NombreCompleto("Ana", null, "Pérez", null);

    private final UsuarioRepository usuarioRepository;
    private final EntityManager entityManager;

    @Autowired
    UsuarioRepositoryIntegrationTest(UsuarioRepository usuarioRepository, EntityManager entityManager) {
        this.usuarioRepository = usuarioRepository;
        this.entityManager = entityManager;
    }

    @Test
    void guardar_asignaIdYValoresPorDefecto() {
        Usuario guardado = usuarioRepository.saveAndFlush(
                new Usuario(ANA, "ana@correo.com", "hash", Rol.HUESPED));

        assertThat(guardado.getIdUsuario()).isNotNull();
        assertThat(guardado.isCuentaActiva()).isTrue();
        assertThat(guardado.isRestringido()).isFalse();
    }

    @Test
    void guardar_conLosCuatroNombres() {
        NombreCompleto nombre = new NombreCompleto("María", "José", "Rodríguez", "O'Neill");
        Usuario guardado = usuarioRepository.saveAndFlush(
                new Usuario(nombre, "maria@correo.com", "hash", Rol.ANFITRION));
        entityManager.clear(); // Obliga a leer desde la base de datos y no desde la caché de JPA

        Usuario leido = usuarioRepository.findById(guardado.getIdUsuario()).orElseThrow();
        assertThat(leido.getNombre()).isEqualTo(nombre);
        assertThat(leido.getRol()).isEqualTo(Rol.ANFITRION);
    }

    @Test
    void existsByCorreoIgnoreCase_encuentraSinDistinguirMayusculas() {
        usuarioRepository.saveAndFlush(new Usuario(ANA, "ana@correo.com", "hash", Rol.HUESPED));

        assertThat(usuarioRepository.existsByCorreoIgnoreCase("ANA@Correo.com")).isTrue();
        assertThat(usuarioRepository.existsByCorreoIgnoreCase("otra@correo.com")).isFalse();
    }

    @Test
    void indiceUnico_rechazaElMismoCorreoConOtrasMayusculasAunqueCambieElRol() {
        usuarioRepository.saveAndFlush(new Usuario(ANA, "ana@correo.com", "hash", Rol.HUESPED));
        Usuario duplicado = new Usuario(ANA, "ANA@CORREO.COM", "hash", Rol.ANFITRION);

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(duplicado))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_usuario_correo");
    }

    @Test
    void checkDeLaBaseDeDatos_rechazaPrimerNombreEnBlanco() {
        Usuario enBlanco = new Usuario(new NombreCompleto("   ", null, "Pérez", null),
                "blanco@correo.com", "hash", Rol.HUESPED);

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(enBlanco))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_usuario_primer_nombre");
    }
}
