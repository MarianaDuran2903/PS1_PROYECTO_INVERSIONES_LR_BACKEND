package com.inversioneslr.backend.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Lo que el sistema necesita de una pasarela de pagos. La única implementación es Stripe Checkout en modo de
 * pruebas; los servicios dependen de esta interfaz para no conocer el SDK y para probarse sin red.
 */
public interface PasarelaPago {

    /**
     * Abre una sesión de pago por el monto de una reserva.
     *
     * @throws com.inversioneslr.backend.exception.PasarelaNoDisponibleException si la pasarela no responde o no
     *                                                                           está configurada
     */
    SesionCreada crearSesion(SolicitudSesion solicitud);

    /**
     * Lo que la pasarela dice hoy de una sesión; vacío si no la conoce. Es la consulta directa que respalda al
     * aviso (webhook), por si este tarda o no puede llegar.
     *
     * @throws com.inversioneslr.backend.exception.PasarelaNoDisponibleException si la pasarela no responde
     */
    Optional<EstadoSesion> consultarSesion(String idSesion);

    /**
     * Verifica la firma de un aviso de la pasarela y lo traduce. Un aviso sin firma válida nunca llega a producir
     * un efecto.
     *
     * @throws com.inversioneslr.backend.exception.FirmaWebhookInvalidaException si la firma falta o no es válida
     * @throws com.inversioneslr.backend.exception.PasarelaNoDisponibleException si no hay secreto de firma
     */
    EventoPasarela leerEvento(String cuerpo, String firma);

    /**
     * Lo necesario para abrir una sesión. El monto y la moneda son los de la reserva.
     */
    record SolicitudSesion(Long idReserva, String descripcion, BigDecimal monto, String moneda,
                           OffsetDateTime venceEn, String urlExito, String urlCancelacion) {
    }

    record SesionCreada(String idSesion, String url, OffsetDateTime venceEn) {
    }

    /**
     * Situación de una sesión según la pasarela, con lo que se cobró si se pagó.
     */
    record EstadoSesion(String idSesion, Situacion situacion, String idPaymentIntent, BigDecimal monto,
                        String moneda) {

        public enum Situacion {
            ABIERTA, PAGADA, EXPIRADA
        }
    }

    /**
     * Aviso de la pasarela ya verificado y reducido a lo que el sistema usa. Los avisos que no interesan llegan
     * como IGNORADO.
     *
     * @param idSesion        sesión a la que se refiere, en los avisos de pago aprobado y de sesión vencida
     * @param idReserva       reserva a la que se refiere, en los avisos de pago rechazado
     * @param codigoRechazo   código del rechazo, solo en PAGO_RECHAZADO
     */
    record EventoPasarela(String idEvento, Tipo tipo, String idSesion, Long idReserva, String idPaymentIntent,
                          BigDecimal monto, String moneda, String codigoRechazo) {

        public enum Tipo {
            PAGO_APROBADO, PAGO_RECHAZADO, SESION_EXPIRADA, IGNORADO
        }

        public static EventoPasarela ignorado(String idEvento) {
            return new EventoPasarela(idEvento, Tipo.IGNORADO, null, null, null, null, null, null);
        }

        public static EventoPasarela pagoAprobado(String idEvento, String idSesion, String idPaymentIntent,
                                                  BigDecimal monto, String moneda) {
            return new EventoPasarela(idEvento, Tipo.PAGO_APROBADO, idSesion, null, idPaymentIntent, monto, moneda,
                    null);
        }

        public static EventoPasarela pagoRechazado(String idEvento, Long idReserva, String idPaymentIntent,
                                                   String codigoRechazo) {
            return new EventoPasarela(idEvento, Tipo.PAGO_RECHAZADO, null, idReserva, idPaymentIntent, null, null,
                    codigoRechazo);
        }

        public static EventoPasarela sesionExpirada(String idEvento, String idSesion) {
            return new EventoPasarela(idEvento, Tipo.SESION_EXPIRADA, idSesion, null, null, null, null, null);
        }
    }
}
