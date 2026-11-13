package com.inversioneslr.backend.model.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.inversioneslr.backend.model.enums.EstadoPublicacion.ACTIVA;
import static com.inversioneslr.backend.model.enums.EstadoPublicacion.BLOQUEADA;
import static com.inversioneslr.backend.model.enums.EstadoPublicacion.BORRADOR;
import static com.inversioneslr.backend.model.enums.EstadoPublicacion.PAUSADA;
import static com.inversioneslr.backend.model.enums.EstadoPublicacion.PENDIENTE_REVISION;
import static org.assertj.core.api.Assertions.assertThat;

class EstadoPublicacionTest {

    private static final Map<EstadoPublicacion, Set<EstadoPublicacion>> PERMITIDAS = new EnumMap<>(Map.of(
            BORRADOR, EnumSet.of(PENDIENTE_REVISION),
            PENDIENTE_REVISION, EnumSet.of(ACTIVA, BORRADOR, BLOQUEADA),
            ACTIVA, EnumSet.of(PAUSADA, BLOQUEADA),
            PAUSADA, EnumSet.of(ACTIVA, BLOQUEADA),
            BLOQUEADA, EnumSet.of(ACTIVA)));

    @Test
    @DisplayName("La matriz permite exactamente las transiciones del flujo de revisión y ninguna más")
    void puedePasarA_cubreTodasLasCombinaciones() {
        for (EstadoPublicacion origen : EstadoPublicacion.values()) {
            for (EstadoPublicacion destino : EstadoPublicacion.values()) {
                assertThat(origen.puedePasarA(destino))
                        .as("%s -> %s", origen, destino)
                        .isEqualTo(PERMITIDAS.get(origen).contains(destino));
            }
        }
    }

    @Test
    @DisplayName("Solo ACTIVA puede pausarse")
    void puedePasarA_soloActivaVaAPausada() {
        for (EstadoPublicacion origen : EstadoPublicacion.values()) {
            assertThat(origen.puedePasarA(PAUSADA)).isEqualTo(origen == ACTIVA);
        }
    }

    @Test
    @DisplayName("Se bloquea una publicación en revisión, activa o pausada; un borrador no y una bloqueada tampoco")
    void puedePasarA_bloqueoDesdeRevisionActivaOPausada() {
        for (EstadoPublicacion origen : EstadoPublicacion.values()) {
            assertThat(origen.puedePasarA(BLOQUEADA))
                    .as("%s -> BLOQUEADA", origen)
                    .isEqualTo(origen == PENDIENTE_REVISION || origen == ACTIVA || origen == PAUSADA);
        }
    }

    @Test
    @DisplayName("Solo se reactiva una publicación pausada o bloqueada")
    void permiteReactivacion_soloPausadaOBloqueada() {
        for (EstadoPublicacion estado : EstadoPublicacion.values()) {
            assertThat(estado.permiteReactivacion())
                    .as("%s", estado)
                    .isEqualTo(estado == PAUSADA || estado == BLOQUEADA);
        }
    }

    @Test
    @DisplayName("Una BLOQUEADA solo puede volver a ACTIVA, por decisión del Administrador")
    void puedePasarA_bloqueadaSoloVuelveAActiva() {
        for (EstadoPublicacion destino : EstadoPublicacion.values()) {
            assertThat(BLOQUEADA.puedePasarA(destino)).as("BLOQUEADA -> %s", destino).isEqualTo(destino == ACTIVA);
        }
    }

    @Test
    @DisplayName("Solo BORRADOR puede enviarse a revisión")
    void puedePasarA_soloBorradorVaARevision() {
        for (EstadoPublicacion origen : EstadoPublicacion.values()) {
            assertThat(origen.puedePasarA(PENDIENTE_REVISION)).isEqualTo(origen == BORRADOR);
        }
    }
}
