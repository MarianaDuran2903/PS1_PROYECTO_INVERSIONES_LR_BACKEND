package com.inversioneslr.backend.exception;

import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TransicionNoPermitidaExceptionTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "PENDIENTE_REVISION|La publicación ya está en revisión",
            "ACTIVA|La publicación ya está activa",
            "PAUSADA|La publicación está pausada: el Administrador gestiona su revisión",
            "BLOQUEADA|La publicación está bloqueada y no se puede enviar a revisión",
            "BORRADOR|La publicación no se puede enviar a revisión en su estado actual"
    })
    void alEnviarARevision_explicaElMotivoSegunElEstado(EstadoPublicacion estado, String mensaje) {
        assertThat(TransicionNoPermitidaException.alEnviarARevision(estado)).hasMessage(mensaje);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "BORRADOR|La publicación está en borrador: el Anfitrión aún no la envía a revisión",
            "ACTIVA|La publicación ya está activa",
            "PAUSADA|La publicación está pausada y no está pendiente de revisión",
            "BLOQUEADA|La publicación está bloqueada y no está pendiente de revisión",
            "PENDIENTE_REVISION|La publicación no se puede revisar en su estado actual"
    })
    void alRevisar_explicaElMotivoSegunElEstado(EstadoPublicacion estado, String mensaje) {
        assertThat(TransicionNoPermitidaException.alRevisar(estado)).hasMessage(mensaje);
    }
}
