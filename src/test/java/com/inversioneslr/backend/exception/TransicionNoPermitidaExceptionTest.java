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

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "BORRADOR|La publicación está en borrador: aún no se ha publicado, no hay nada que pausar",
            "PENDIENTE_REVISION|La publicación está en revisión: solo se puede pausar una publicación activa",
            "PAUSADA|La publicación ya está pausada",
            "BLOQUEADA|La publicación está bloqueada y no se puede pausar",
            "ACTIVA|La publicación no se puede pausar en su estado actual"
    })
    void alPausar_explicaElMotivoSegunElEstado(EstadoPublicacion estado, String mensaje) {
        assertThat(TransicionNoPermitidaException.alPausar(estado)).hasMessage(mensaje);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "BORRADOR|La publicación está en borrador: aún no se ha publicado, no hay nada que bloquear",
            "BLOQUEADA|La publicación ya está bloqueada",
            "PENDIENTE_REVISION|La publicación no se puede bloquear en su estado actual",
            "ACTIVA|La publicación no se puede bloquear en su estado actual",
            "PAUSADA|La publicación no se puede bloquear en su estado actual"
    })
    void alBloquear_explicaElMotivoSegunElEstado(EstadoPublicacion estado, String mensaje) {
        assertThat(TransicionNoPermitidaException.alBloquear(estado)).hasMessage(mensaje);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "ACTIVA|La publicación ya está activa",
            "BORRADOR|La publicación está en borrador: el Anfitrión debe enviarla a revisión",
            "PENDIENTE_REVISION|La publicación está en revisión: se aprueba, no se reactiva",
            "PAUSADA|La publicación no se puede reactivar en su estado actual",
            "BLOQUEADA|La publicación no se puede reactivar en su estado actual"
    })
    void alReactivar_explicaElMotivoSegunElEstado(EstadoPublicacion estado, String mensaje) {
        assertThat(TransicionNoPermitidaException.alReactivar(estado)).hasMessage(mensaje);
    }
}
