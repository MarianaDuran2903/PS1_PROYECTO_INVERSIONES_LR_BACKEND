package com.inversioneslr.backend.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MotivoRechazoPagoTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "card_declined|Su tarjeta fue rechazada. Intente con otra tarjeta.",
            "generic_decline|Su tarjeta fue rechazada. Intente con otra tarjeta.",
            "insufficient_funds|La tarjeta no tiene fondos suficientes.",
            "expired_card|La tarjeta está vencida.",
            "incorrect_cvc|El código de seguridad (CVC) de la tarjeta es incorrecto.",
            "invalid_cvc|El código de seguridad (CVC) de la tarjeta es incorrecto.",
            "incorrect_number|El número de la tarjeta no es válido.",
            "invalid_number|El número de la tarjeta no es válido.",
            "processing_error|Ocurrió un error al procesar el pago. Intente de nuevo.",
            "MONTO_NO_COINCIDE|No se pudo verificar el monto del pago. Contacte a soporte."
    })
    void mensajePara_traduceLosCodigosConocidos(String codigo, String mensaje) {
        assertThat(MotivoRechazoPago.mensajePara(codigo)).isEqualTo(mensaje);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"lost_card", "stolen_card", "fraudulent"})
    @DisplayName("Una tarjeta perdida, robada o fraudulenta no revela el motivo exacto")
    void mensajePara_losMotivosAntifraudeSeOcultan(String codigo) {
        assertThat(MotivoRechazoPago.mensajePara(codigo))
                .isEqualTo("La tarjeta no pudo ser procesada. Intente con otra tarjeta.")
                .doesNotContainIgnoringCase("robada")
                .doesNotContainIgnoringCase("perdida")
                .doesNotContainIgnoringCase("fraude");
    }

    @Test
    void mensajePara_unCodigoDesconocido_usaElMensajeGenerico() {
        assertThat(MotivoRechazoPago.mensajePara("codigo_nuevo_de_stripe"))
                .isEqualTo(MotivoRechazoPago.MENSAJE_GENERICO);
    }

    @Test
    void mensajePara_sinCodigo_noHayRechazo() {
        assertThat(MotivoRechazoPago.mensajePara(null)).isNull();
    }
}
