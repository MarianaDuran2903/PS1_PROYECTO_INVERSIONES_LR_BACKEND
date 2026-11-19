package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicacionBloqueoTest {

    private static final String MOTIVO = "Las fotos pertenecen a otro alojamiento y la dirección no existe";

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PENDIENTE_REVISION", "ACTIVA", "PAUSADA"})
    @DisplayName("Una publicación en revisión, activa o pausada queda BLOQUEADA")
    void bloquear_estadoPermitido_quedaBloqueada(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        publicacion.bloquear(MOTIVO);

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BLOQUEADA);
    }

    @Test
    @DisplayName("Bloquear conserva la información de la publicación")
    void bloquear_conservaLaInformacion() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);

        publicacion.bloquear(MOTIVO);

        assertThat(publicacion.getTitulo()).isEqualTo("Apartamento frente al mar");
        assertThat(publicacion.getDescripcion()).isEqualTo("Dos habitaciones");
        assertThat(publicacion.getDireccionTextual()).isEqualTo("Calle 5 # 3-20");
        assertThat(publicacion.getPrecioNoche()).isEqualByComparingTo("250000.00");
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "BLOQUEADA"})
    @DisplayName("Un borrador no se publicó y una bloqueada ya lo está: no cambian")
    void bloquear_estadoNoPermitido_lanzaYConservaElEstado(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        assertThatThrownBy(() -> publicacion.bloquear(MOTIVO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No se puede pasar de " + estado + " a BLOQUEADA");

        assertThat(publicacion.getEstado()).isEqualTo(estado);
    }

    @Test
    @DisplayName("Bloquear guarda el motivo sin los espacios sobrantes")
    void bloquear_guardaElMotivoSinEspacios() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);

        publicacion.bloquear("  " + MOTIVO + "  ");

        assertThat(publicacion.getMotivoBloqueo()).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("Una publicación que no está bloqueada no tiene motivo de bloqueo")
    void motivoBloqueo_sinBloqueo_esNulo() {
        assertThat(publicacion(EstadoPublicacion.ACTIVA).getMotivoBloqueo()).isNull();
    }

    @ParameterizedTest(name = "motivo [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Sin motivo no se bloquea y la publicación conserva su estado")
    void bloquear_sinMotivo_lanzaYConservaElEstado(String motivo) {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);

        assertThatThrownBy(() -> publicacion.bloquear(motivo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El motivo del bloqueo es obligatorio");

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.ACTIVA);
        assertThat(publicacion.getMotivoBloqueo()).isNull();
    }

    private static Publicacion publicacion(EstadoPublicacion estado) {
        Usuario anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", "hash", Rol.ANFITRION);
        PoliticaCancelacion politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "Bocagrande", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, politica, datos, new Ciudad("Cartagena"),
                new TipoAlojamiento("Apartamento"));
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        return publicacion;
    }
}
