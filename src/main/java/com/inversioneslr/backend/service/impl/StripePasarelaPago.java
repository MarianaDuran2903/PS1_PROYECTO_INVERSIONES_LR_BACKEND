package com.inversioneslr.backend.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.config.PagoProperties;
import com.inversioneslr.backend.exception.FirmaWebhookInvalidaException;
import com.inversioneslr.backend.exception.PasarelaNoDisponibleException;
import com.inversioneslr.backend.service.PasarelaPago;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Pasarela de pagos con Stripe Checkout. La clave se pasa en cada llamada y no se guarda en el estado global del
 * SDK. Los avisos (webhooks) se leen del JSON después de verificar su firma, en lugar de deserializarlos con las
 * clases del SDK, que fallan cuando la versión de la API del aviso no es la del SDK.
 */
@Service
public class StripePasarelaPago implements PasarelaPago {

    /** Metadato con el que Stripe devuelve la reserva en sus avisos, también en los del payment intent. */
    static final String METADATO_RESERVA = "id_reserva";

    private static final Logger log = LoggerFactory.getLogger(StripePasarelaPago.class);

    private static final int TIEMPO_CONEXION_MS = 10_000;
    private static final int TIEMPO_LECTURA_MS = 20_000;
    private static final int REINTENTOS_DE_RED = 2;
    /** La moneda con dos decimales: Stripe recibe el monto en la unidad menor (centavos). */
    private static final int DECIMALES = 2;
    private static final String CODIGO_RECURSO_INEXISTENTE = "resource_missing";
    private static final String CODIGO_RECHAZO_POR_DEFECTO = "payment_failed";

    private final PagoProperties propiedades;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StripePasarelaPago(PagoProperties propiedades, ObjectMapper objectMapper, Clock clock) {
        this.propiedades = propiedades;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public SesionCreada crearSesion(SolicitudSesion solicitud) {
        String idReserva = String.valueOf(solicitud.idReserva());
        SessionCreateParams parametros = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(solicitud.urlExito())
                .setCancelUrl(solicitud.urlCancelacion())
                .setClientReferenceId(idReserva)
                .setExpiresAt(solicitud.venceEn().toEpochSecond())
                .putMetadata(METADATO_RESERVA, idReserva)
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .putMetadata(METADATO_RESERVA, idReserva)
                        .setDescription(solicitud.descripcion())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(solicitud.moneda().toLowerCase(Locale.ROOT))
                                .setUnitAmount(aUnidadMenor(solicitud.monto()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(solicitud.descripcion())
                                        .build())
                                .build())
                        .build())
                .build();
        try {
            Session sesion = Session.create(parametros, opciones());
            return new SesionCreada(sesion.getId(), sesion.getUrl(),
                    OffsetDateTime.ofInstant(Instant.ofEpochSecond(sesion.getExpiresAt()), ZoneOffset.UTC));
        } catch (StripeException error) {
            throw new PasarelaNoDisponibleException(error);
        }
    }

    @Override
    public Optional<EstadoSesion> consultarSesion(String idSesion) {
        try {
            Session sesion = Session.retrieve(idSesion, opciones());
            return Optional.of(new EstadoSesion(sesion.getId(), situacionDe(sesion), sesion.getPaymentIntent(),
                    deUnidadMenor(sesion.getAmountTotal()), monedaDe(sesion.getCurrency())));
        } catch (InvalidRequestException error) {
            if (CODIGO_RECURSO_INEXISTENTE.equals(error.getCode())) {
                return Optional.empty();
            }
            throw new PasarelaNoDisponibleException(error);
        } catch (StripeException error) {
            throw new PasarelaNoDisponibleException(error);
        }
    }

    @Override
    public EventoPasarela leerEvento(String cuerpo, String firma) {
        PagoProperties.Stripe stripe = propiedades.stripe();
        if (!stripe.tieneClaveWebhook()) {
            throw new PasarelaNoDisponibleException();
        }
        verificarFirma(cuerpo, firma, stripe);

        JsonNode evento;
        try {
            evento = objectMapper.readTree(cuerpo);
        } catch (JsonProcessingException error) {
            // La firma es válida, así que el aviso es de Stripe; si no se entiende, no hay nada que aplicar.
            log.warn("Aviso de pago ilegible {} {}", kv("accion", "LEER_AVISO_PAGO"), kv("resultado", "FALLIDO"));
            return EventoPasarela.ignorado(null);
        }
        String idEvento = texto(evento, "id");
        JsonNode objeto = evento.path("data").path("object");
        String tipo = texto(evento, "type");
        if (idEvento == null || tipo == null) {
            return EventoPasarela.ignorado(idEvento);
        }
        return switch (tipo) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" ->
                    pagoAprobado(idEvento, objeto);
            case "checkout.session.expired" ->
                    EventoPasarela.sesionExpirada(idEvento, texto(objeto, "id"));
            case "payment_intent.payment_failed" -> pagoRechazado(idEvento, objeto);
            default -> EventoPasarela.ignorado(idEvento);
        };
    }

    private void verificarFirma(String cuerpo, String firma, PagoProperties.Stripe stripe) {
        if (firma == null || firma.isBlank()) {
            throw new FirmaWebhookInvalidaException();
        }
        try {
            Webhook.Signature.verifyHeader(cuerpo, firma, stripe.claveWebhook(), stripe.toleranciaFirmaSegundos(),
                    clock);
        } catch (SignatureVerificationException | IllegalArgumentException error) {
            // IllegalArgumentException: encabezado con una marca de tiempo que no es un número.
            throw new FirmaWebhookInvalidaException();
        }
    }

    /**
     * El pago se aprobó solo si la sesión quedó pagada: con medios de pago diferidos, "completed" puede llegar
     * antes del cobro.
     */
    private EventoPasarela pagoAprobado(String idEvento, JsonNode sesion) {
        String idSesion = texto(sesion, "id");
        if (idSesion == null || !"paid".equals(texto(sesion, "payment_status"))) {
            return EventoPasarela.ignorado(idEvento);
        }
        JsonNode total = sesion.path("amount_total");
        BigDecimal monto = total.isIntegralNumber() ? BigDecimal.valueOf(total.asLong(), DECIMALES) : null;
        return EventoPasarela.pagoAprobado(idEvento, idSesion, texto(sesion, "payment_intent"), monto,
                monedaDe(texto(sesion, "currency")));
    }

    /**
     * El payment intent no conoce la sesión, pero hereda sus metadatos: de ahí sale la reserva. Sin reserva el
     * aviso no es de este sistema y se ignora.
     */
    private EventoPasarela pagoRechazado(String idEvento, JsonNode paymentIntent) {
        Long idReserva = idReservaDe(texto(paymentIntent.path("metadata"), METADATO_RESERVA));
        if (idReserva == null) {
            return EventoPasarela.ignorado(idEvento);
        }
        JsonNode error = paymentIntent.path("last_payment_error");
        String codigo = Optional.ofNullable(texto(error, "decline_code"))
                .or(() -> Optional.ofNullable(texto(error, "code")))
                .orElse(CODIGO_RECHAZO_POR_DEFECTO);
        return EventoPasarela.pagoRechazado(idEvento, idReserva, texto(paymentIntent, "id"), codigo);
    }

    private RequestOptions opciones() {
        PagoProperties.Stripe stripe = propiedades.stripe();
        if (!stripe.tieneClaveSecreta()) {
            throw new PasarelaNoDisponibleException();
        }
        return RequestOptions.builder()
                .setApiKey(stripe.claveSecreta())
                .setConnectTimeout(TIEMPO_CONEXION_MS)
                .setReadTimeout(TIEMPO_LECTURA_MS)
                .setMaxNetworkRetries(REINTENTOS_DE_RED)
                .build();
    }

    private static EstadoSesion.Situacion situacionDe(Session sesion) {
        if ("complete".equals(sesion.getStatus()) && "paid".equals(sesion.getPaymentStatus())) {
            return EstadoSesion.Situacion.PAGADA;
        }
        if ("expired".equals(sesion.getStatus())) {
            return EstadoSesion.Situacion.EXPIRADA;
        }
        return EstadoSesion.Situacion.ABIERTA;
    }

    private static long aUnidadMenor(BigDecimal monto) {
        return monto.movePointRight(DECIMALES).longValueExact();
    }

    private static BigDecimal deUnidadMenor(Long unidadMenor) {
        return unidadMenor == null ? null : BigDecimal.valueOf(unidadMenor, DECIMALES);
    }

    private static String monedaDe(String moneda) {
        return moneda == null ? null : moneda.toUpperCase(Locale.ROOT);
    }

    private static Long idReservaDe(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    /** El texto del campo, o nulo si falta o está en blanco. */
    private static String texto(JsonNode nodo, String campo) {
        String valor = nodo.path(campo).asText(null);
        return valor == null || valor.isBlank() ? null : valor;
    }
}
