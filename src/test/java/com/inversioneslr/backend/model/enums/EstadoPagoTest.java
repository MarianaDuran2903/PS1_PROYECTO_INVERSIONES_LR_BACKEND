package com.inversioneslr.backend.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoPagoTest {

    @ParameterizedTest(name = "{0} abierto: {1}")
    @CsvSource({"PENDIENTE, true", "RECHAZADO, true", "APROBADO, false", "EXPIRADO, false"})
    void estaAbierto_soloLasSesionesQueAdmitenPago(EstadoPago estado, boolean abierto) {
        assertThat(estado.estaAbierto()).isEqualTo(abierto);
    }

    @Test
    void abiertos_sonLosEstadosAbiertos() {
        assertThat(EstadoPago.ABIERTOS).containsExactlyInAnyOrder(EstadoPago.PENDIENTE, EstadoPago.RECHAZADO);
    }
}
