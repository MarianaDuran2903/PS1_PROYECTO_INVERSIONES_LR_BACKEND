package com.inversioneslr.backend.config;

import com.inversioneslr.backend.service.ExpiracionReservasService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiracionReservasProgramadaTest {

    @Mock
    private ExpiracionReservasService expiracionReservasService;

    @InjectMocks
    private ExpiracionReservasProgramada tarea;

    @Test
    void vencerReservasSinPago_conReservasVencidas_llamaAlServicio() {
        when(expiracionReservasService.vencerPendientes()).thenReturn(3);

        tarea.vencerReservasSinPago();

        verify(expiracionReservasService).vencerPendientes();
    }

    @Test
    void vencerReservasSinPago_sinReservasVencidas_terminaSinError() {
        when(expiracionReservasService.vencerPendientes()).thenReturn(0);

        assertThatCode(tarea::vencerReservasSinPago).doesNotThrowAnyException();
    }

    @Test
    void vencerReservasSinPago_siElServicioFalla_noDetieneLaTarea() {
        when(expiracionReservasService.vencerPendientes()).thenThrow(new IllegalStateException("base caída"));

        assertThatCode(tarea::vencerReservasSinPago).doesNotThrowAnyException();
    }
}
