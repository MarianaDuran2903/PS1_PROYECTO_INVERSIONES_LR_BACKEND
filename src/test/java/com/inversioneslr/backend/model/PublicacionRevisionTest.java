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

class PublicacionRevisionTest {

    @Test
    @DisplayName("Aprobar una publicación pendiente la deja ACTIVA")
    void aprobar_pendiente_quedaActiva() {
        Publicacion publicacion = publicacion(EstadoPublicacion.PENDIENTE_REVISION);

        publicacion.aprobar();

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.ACTIVA);
    }

    @Test
    @DisplayName("Rechazar una publicación pendiente la devuelve a BORRADOR")
    void rechazar_pendiente_vuelveABorrador() {
        Publicacion publicacion = publicacion(EstadoPublicacion.PENDIENTE_REVISION);

        publicacion.rechazar();

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Solo una publicación pendiente puede aprobarse")
    void aprobar_noPendiente_lanzaYConservaElEstado(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        assertThatThrownBy(publicacion::aprobar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La publicación no está pendiente de revisión: " + estado);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Solo una publicación pendiente puede rechazarse")
    void rechazar_noPendiente_lanzaYConservaElEstado(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        assertThatThrownBy(publicacion::rechazar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La publicación no está pendiente de revisión: " + estado);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
    }

    @Test
    void solicitarRevision_borrador_pasaAPendiente() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR);

        publicacion.solicitarRevision();

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PENDIENTE_REVISION);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PENDIENTE_REVISION", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    void solicitarRevision_fueraDeBorrador_lanzaYConservaElEstado(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        assertThatThrownBy(publicacion::solicitarRevision)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No se puede pasar de " + estado + " a PENDIENTE_REVISION");

        assertThat(publicacion.getEstado()).isEqualTo(estado);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(EstadoPublicacion.class)
    void estaPendienteDeRevision_soloEnPendienteRevision(EstadoPublicacion estado) {
        assertThat(publicacion(estado).estaPendienteDeRevision())
                .isEqualTo(estado == EstadoPublicacion.PENDIENTE_REVISION);
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
