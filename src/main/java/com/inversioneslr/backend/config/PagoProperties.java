package com.inversioneslr.backend.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración del pago de reservas con Stripe Checkout. Solo modo de pruebas: una clave real
 * ({@code sk_live_...}) impide arrancar la aplicación, así que ningún cobro puede ser verdadero.
 */
@Validated
@ConfigurationProperties(prefix = "pagos")
public record PagoProperties(

        // Moneda en que se cobra, código ISO de tres letras.
        @NotBlank
        @Pattern(regexp = "[A-Za-z]{3}", message = "La moneda debe ser un código ISO de tres letras")
        String moneda,

        // Minutos que la sesión de pago de Stripe sigue abierta. Stripe exige al menos 30; se pide un minuto más
        // para que la demora de la llamada no deje la sesión por debajo del mínimo.
        @Min(31)
        @Max(1440)
        long minutosSesion,

        // Minutos que la reserva sobrevive a su sesión de pago, para dar tiempo a que llegue el aviso del pago.
        @PositiveOrZero
        long minutosGracia,

        // Sesiones de pago que se pueden abrir por reserva; evita retener las fechas abriéndolas sin pagar.
        @Positive
        int intentosMaximos,

        // Páginas del frontend a las que Stripe devuelve al Huésped. {idReserva} se reemplaza por la reserva.
        @NotBlank
        String urlExito,
        @NotBlank
        String urlCancelacion,

        @Valid
        @NotNull
        Stripe stripe
) {

    static final String MARCADOR_RESERVA = "{idReserva}";

    /**
     * Claves de Stripe. Ambas llegan por variables de entorno; sin la clave secreta los pagos responden 502 pero
     * la aplicación arranca.
     */
    public record Stripe(

            @Pattern(regexp = "^$|^(sk|rk)_test_\\S+$",
                    message = "Solo se admiten claves de prueba de Stripe (sk_test_...)")
            String claveSecreta,

            // Secreto de firma del webhook (whsec_...), distinto de la clave secreta.
            String claveWebhook,

            // Antigüedad máxima, en segundos, de un aviso firmado; los más viejos se rechazan.
            @Positive
            long toleranciaFirmaSegundos
    ) {

        public boolean tieneClaveSecreta() {
            return claveSecreta != null && !claveSecreta.isBlank();
        }

        public boolean tieneClaveWebhook() {
            return claveWebhook != null && !claveWebhook.isBlank();
        }
    }

    public String urlExitoDe(Long idReserva) {
        return urlExito.replace(MARCADOR_RESERVA, String.valueOf(idReserva));
    }

    public String urlCancelacionDe(Long idReserva) {
        return urlCancelacion.replace(MARCADOR_RESERVA, String.valueOf(idReserva));
    }
}
