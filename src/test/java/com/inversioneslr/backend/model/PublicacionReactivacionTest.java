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

class PublicacionReactivacionTest {

    private static final String MOTIVO = "Las fotos pertenecen a otro alojamiento y la dirección no existe";

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PAUSADA", "BLOQUEADA"})
    @DisplayName("Una publicación pausada o bloqueada vuelve a ACTIVA")
    void reactivar_pausadaOBloqueada_quedaActiva(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        publicacion.reactivar();

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.ACTIVA);
    }

    @Test
    @DisplayName("Reactivar una bloqueada limpia el motivo del bloqueo")
    void reactivar_bloqueada_limpiaElMotivo() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);
        publicacion.bloquear(MOTIVO);
        assertThat(publicacion.getMotivoBloqueo()).isEqualTo(MOTIVO);

        publicacion.reactivar();

        assertThat(publicacion.getMotivoBloqueo()).isNull();
    }

    @Test
    @DisplayName("Reactivar conserva la información de la publicación")
    void reactivar_conservaLaInformacion() {
        Publicacion publicacion = publicacion(EstadoPublicacion.PAUSADA);

        publicacion.reactivar();

        assertThat(publicacion.getTitulo()).isEqualTo("Apartamento frente al mar");
        assertThat(publicacion.getDireccionTextual()).isEqualTo("Calle 5 # 3-20");
        assertThat(publicacion.getPrecioNoche()).isEqualByComparingTo("250000.00");
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "PENDIENTE_REVISION", "ACTIVA"})
    @DisplayName("Solo una publicación pausada o bloqueada se reactiva")
    void reactivar_otroEstado_lanzaYConservaElEstado(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);

        assertThatThrownBy(publicacion::reactivar)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("La publicación no está pausada ni bloqueada: " + estado);

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
        if (estado == EstadoPublicacion.BLOQUEADA) {
            ReflectionTestUtils.setField(publicacion, "motivoBloqueo", MOTIVO);
        }
        return publicacion;
    }
}
