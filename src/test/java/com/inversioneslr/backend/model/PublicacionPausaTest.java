package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicacionPausaTest {

    @Test
    @DisplayName("Pausar una publicación ACTIVA la deja PAUSADA")
    void pausar_activa_quedaPausada() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);

        publicacion.pausar();

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PAUSADA);
    }

    @Test
    @DisplayName("Pausar conserva la información de la publicación")
    void pausar_conservaLaInformacion() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);

        publicacion.pausar();

        assertThat(publicacion.getTitulo()).isEqualTo("Apartamento frente al mar");
        assertThat(publicacion.getDescripcion()).isEqualTo("Dos habitaciones");
        assertThat(publicacion.getDireccionTextual()).isEqualTo("Calle 5 # 3-20");
        assertThat(publicacion.getPrecioNoche()).isEqualByComparingTo("250000.00");
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Solo una publicación ACTIVA puede pausarse")
    void pausar_noActiva_lanzaYConservaElEstado(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        assertThatThrownBy(publicacion::pausar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No se puede pasar de " + estado + " a PAUSADA");

        assertThat(publicacion.getEstado()).isEqualTo(estado);
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
