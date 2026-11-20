package com.inversioneslr.backend.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoReservaTest {

    @Test
    void ocupaFechas_soloLaCanceladaLasLibera() {
        assertThat(EstadoReserva.PENDIENTE_PAGO.ocupaFechas()).isTrue();
        assertThat(EstadoReserva.CONFIRMADA.ocupaFechas()).isTrue();
        assertThat(EstadoReserva.REVISION_MANUAL.ocupaFechas()).isTrue();
        assertThat(EstadoReserva.CANCELADA.ocupaFechas()).isFalse();
    }
}
