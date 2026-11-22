package com.inversioneslr.backend.model;

import java.util.Map;

/**
 * Mensaje para el Huésped según el código con que Stripe (o el propio sistema) rechazó un pago. Nunca se muestra
 * el texto crudo de Stripe: podría revelar el motivo exacto de un rechazo antifraude.
 */
public final class MotivoRechazoPago {

    /** El monto cobrado no es el de la reserva; lo detecta el sistema, no Stripe. */
    public static final String MONTO_NO_COINCIDE = "MONTO_NO_COINCIDE";

    static final String MENSAJE_GENERICO = "El pago fue rechazado. Intente con otra tarjeta.";

    private static final String MENSAJE_TARJETA_NO_PROCESADA = "La tarjeta no pudo ser procesada. Intente con otra tarjeta.";

    private static final Map<String, String> MENSAJES = Map.ofEntries(
            Map.entry("card_declined", "Su tarjeta fue rechazada. Intente con otra tarjeta."),
            Map.entry("generic_decline", "Su tarjeta fue rechazada. Intente con otra tarjeta."),
            Map.entry("insufficient_funds", "La tarjeta no tiene fondos suficientes."),
            Map.entry("expired_card", "La tarjeta está vencida."),
            Map.entry("incorrect_cvc", "El código de seguridad (CVC) de la tarjeta es incorrecto."),
            Map.entry("invalid_cvc", "El código de seguridad (CVC) de la tarjeta es incorrecto."),
            Map.entry("incorrect_number", "El número de la tarjeta no es válido."),
            Map.entry("invalid_number", "El número de la tarjeta no es válido."),
            Map.entry("lost_card", MENSAJE_TARJETA_NO_PROCESADA),
            Map.entry("stolen_card", MENSAJE_TARJETA_NO_PROCESADA),
            Map.entry("fraudulent", MENSAJE_TARJETA_NO_PROCESADA),
            Map.entry("processing_error", "Ocurrió un error al procesar el pago. Intente de nuevo."),
            Map.entry(MONTO_NO_COINCIDE, "No se pudo verificar el monto del pago. Contacte a soporte."));

    private MotivoRechazoPago() {
    }

    /**
     * El mensaje que corresponde al código, o nulo si no hubo rechazo (código nulo). Un código que no se
     * conoce recibe el mensaje genérico.
     */
    public static String mensajePara(String codigo) {
        if (codigo == null) {
            return null;
        }
        return MENSAJES.getOrDefault(codigo, MENSAJE_GENERICO);
    }
}
