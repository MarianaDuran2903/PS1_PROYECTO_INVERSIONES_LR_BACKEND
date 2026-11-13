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
            BLOQUEADA, EnumSet.noneOf(EstadoPublicacion.class)));

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
    @DisplayName("Solo BORRADOR puede enviarse a revisión")
    void puedePasarA_soloBorradorVaARevision() {
        for (EstadoPublicacion origen : EstadoPublicacion.values()) {
            assertThat(origen.puedePasarA(PENDIENTE_REVISION)).isEqualTo(origen == BORRADOR);
        }
    }
}
