package com.inversioneslr.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.config.PagoProperties;
import com.inversioneslr.backend.exception.FirmaWebhookInvalidaException;
import com.inversioneslr.backend.exception.PasarelaNoDisponibleException;
import com.inversioneslr.backend.service.PasarelaPago.EstadoSesion;
import com.inversioneslr.backend.service.PasarelaPago.EventoPasarela;
import com.inversioneslr.backend.service.PasarelaPago.SesionCreada;
import com.inversioneslr.backend.service.PasarelaPago.SolicitudSesion;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.checkout.Session;
import com.stripe.net.BearerTokenAuthenticator;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class StripePasarelaPagoTest {

    private static final String SECRETO = "whsec_secreto_de_la_prueba";
    private static final Instant INSTANTE = Instant.parse("2030-06-01T10:00:00Z");
    private static final long MARCA = INSTANTE.getEpochSecond();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final StripePasarelaPago pasarela = pasarela("sk_test_clave", SECRETO);

    private StripePasarelaPago pasarela(String claveSecreta, String claveWebhook) {
        PagoProperties propiedades = new PagoProperties("COP", 35, 5, 3, "https://app.test/{idReserva}",
                "https://app.test/{idReserva}?cancelado=true",
                new PagoProperties.Stripe(claveSecreta, claveWebhook, 300));
        return new StripePasarelaPago(propiedades, objectMapper, Clock.fixed(INSTANTE, ZoneOffset.UTC));
    }

    /** La firma que calcula Stripe: HMAC-SHA256 del texto "marca.cuerpo" con el secreto del webhook. */
    private static String firmar(String cuerpo, long marca, String secreto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] firma = mac.doFinal((marca + "." + cuerpo).getBytes(StandardCharsets.UTF_8));
            return "t=" + marca + ",v1=" + HexFormat.of().formatHex(firma);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private EventoPasarela leer(String cuerpo) {
        return pasarela.leerEvento(cuerpo, firmar(cuerpo, MARCA, SECRETO));
    }

    private static String evento(String tipo, String objeto) {
        return "{\"id\":\"evt_1\",\"type\":\"" + tipo + "\",\"data\":{\"object\":" + objeto + "}}";
    }

    // ------------------------------------------------------------------ avisos: firma

    @Test
    @DisplayName("Un aviso con firma válida se lee")
    void leerEvento_conFirmaValida_loLee() {
        String cuerpo = evento("checkout.session.expired", "{\"id\":\"cs_test_1\"}");

        EventoPasarela evento = leer(cuerpo);

        assertThat(evento.tipo()).isEqualTo(EventoPasarela.Tipo.SESION_EXPIRADA);
        assertThat(evento.idEvento()).isEqualTo("evt_1");
        assertThat(evento.idSesion()).isEqualTo("cs_test_1");
    }

    @ParameterizedTest(name = "firma \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "basura", "t=abc,v1=123", "t=1700000000", "v1=abcdef"})
    @DisplayName("Una firma ausente o mal formada se rechaza")
    void leerEvento_conFirmaAusenteOMalFormada_seRechaza(String firma) {
        String cuerpo = evento("checkout.session.expired", "{\"id\":\"cs_test_1\"}");

        assertThatThrownBy(() -> pasarela.leerEvento(cuerpo, firma))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Una firma hecha con otro secreto se rechaza")
    void leerEvento_conOtroSecreto_seRechaza() {
        String cuerpo = evento("checkout.session.expired", "{\"id\":\"cs_test_1\"}");

        assertThatThrownBy(() -> pasarela.leerEvento(cuerpo, firmar(cuerpo, MARCA, "whsec_otro")))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Un cuerpo modificado después de firmar se rechaza")
    void leerEvento_conCuerpoAlterado_seRechaza() {
        String original = evento("checkout.session.expired", "{\"id\":\"cs_test_1\"}");
        String alterado = evento("checkout.session.expired", "{\"id\":\"cs_test_2\"}");

        assertThatThrownBy(() -> pasarela.leerEvento(alterado, firmar(original, MARCA, SECRETO)))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
    }

    @Test
    @DisplayName("Un aviso firmado hace más de la tolerancia se rechaza, para que no se pueda repetir")
    void leerEvento_conFirmaAntigua_seRechaza() {
        String cuerpo = evento("checkout.session.expired", "{\"id\":\"cs_test_1\"}");

        assertThatThrownBy(() -> pasarela.leerEvento(cuerpo, firmar(cuerpo, MARCA - 301, SECRETO)))
                .isInstanceOf(FirmaWebhookInvalidaException.class);
        assertThat(pasarela.leerEvento(cuerpo, firmar(cuerpo, MARCA - 299, SECRETO)).tipo())
                .isEqualTo(EventoPasarela.Tipo.SESION_EXPIRADA);
    }

    @ParameterizedTest(name = "secreto \"{0}\"")
    @NullAndEmptySource
    @DisplayName("Sin secreto de firma configurado no se puede verificar ningún aviso")
    void leerEvento_sinSecreto_noEstaDisponible(String secreto) {
        StripePasarelaPago sinSecreto = pasarela("sk_test_clave", secreto);
        String cuerpo = evento("checkout.session.expired", "{\"id\":\"cs_test_1\"}");

        assertThatThrownBy(() -> sinSecreto.leerEvento(cuerpo, firmar(cuerpo, MARCA, SECRETO)))
                .isInstanceOf(PasarelaNoDisponibleException.class);
    }

    // ------------------------------------------------------------------ avisos: contenido

    @Test
    @DisplayName("Una sesión pagada es un pago aprobado, con el monto en pesos y la moneda en mayúsculas")
    void leerEvento_sesionCompletadaYPagada_esPagoAprobado() {
        String cuerpo = evento("checkout.session.completed", """
                {"id":"cs_test_1","payment_status":"paid","payment_intent":"pi_1",
                 "amount_total":87500000,"currency":"cop"}""");

        EventoPasarela evento = leer(cuerpo);

        assertThat(evento.tipo()).isEqualTo(EventoPasarela.Tipo.PAGO_APROBADO);
        assertThat(evento.idSesion()).isEqualTo("cs_test_1");
        assertThat(evento.idPaymentIntent()).isEqualTo("pi_1");
        assertThat(evento.monto()).isEqualByComparingTo("875000.00");
        assertThat(evento.moneda()).isEqualTo("COP");
    }

    @Test
    void leerEvento_pagoAsincronoExitoso_esPagoAprobado() {
        String cuerpo = evento("checkout.session.async_payment_succeeded", """
                {"id":"cs_test_1","payment_status":"paid","payment_intent":"pi_1",
                 "amount_total":87500000,"currency":"cop"}""");

        assertThat(leer(cuerpo).tipo()).isEqualTo(EventoPasarela.Tipo.PAGO_APROBADO);
    }

    @Test
    @DisplayName("Una sesión completada pero sin cobrar todavía no es un pago aprobado")
    void leerEvento_sesionCompletadaSinPagar_seIgnora() {
        String cuerpo = evento("checkout.session.completed", """
                {"id":"cs_test_1","payment_status":"unpaid","amount_total":87500000,"currency":"cop"}""");

        assertThat(leer(cuerpo).tipo()).isEqualTo(EventoPasarela.Tipo.IGNORADO);
    }

    @Test
    void leerEvento_sesionPagadaSinIdentificador_seIgnora() {
        String cuerpo = evento("checkout.session.completed", """
                {"payment_status":"paid","amount_total":87500000,"currency":"cop"}""");

        assertThat(leer(cuerpo).tipo()).isEqualTo(EventoPasarela.Tipo.IGNORADO);
    }

    @Test
    @DisplayName("Sin monto el aviso llega con monto nulo: el servicio lo trata como no coincidente")
    void leerEvento_pagoSinMonto_llegaConMontoNulo() {
        String cuerpo = evento("checkout.session.completed", """
                {"id":"cs_test_1","payment_status":"paid","payment_intent":"pi_1","currency":"cop"}""");

        EventoPasarela evento = leer(cuerpo);

        assertThat(evento.tipo()).isEqualTo(EventoPasarela.Tipo.PAGO_APROBADO);
        assertThat(evento.monto()).isNull();
    }

    @Test
    @DisplayName("Un pago rechazado sale con la reserva de los metadatos y el código de rechazo del banco")
    void leerEvento_pagoFallido_traeLaReservaYElCodigoDelBanco() {
        String cuerpo = evento("payment_intent.payment_failed", """
                {"id":"pi_1","metadata":{"id_reserva":"31"},
                 "last_payment_error":{"code":"card_declined","decline_code":"insufficient_funds"}}""");

        EventoPasarela evento = leer(cuerpo);

        assertThat(evento.tipo()).isEqualTo(EventoPasarela.Tipo.PAGO_RECHAZADO);
        assertThat(evento.idReserva()).isEqualTo(31L);
        assertThat(evento.idPaymentIntent()).isEqualTo("pi_1");
        assertThat(evento.codigoRechazo()).isEqualTo("insufficient_funds");
    }

    @Test
    void leerEvento_pagoFallidoSinCodigoDelBanco_usaElCodigoGeneral() {
        String cuerpo = evento("payment_intent.payment_failed", """
                {"id":"pi_1","metadata":{"id_reserva":"31"},"last_payment_error":{"code":"expired_card"}}""");

        assertThat(leer(cuerpo).codigoRechazo()).isEqualTo("expired_card");
    }

    @Test
    void leerEvento_pagoFallidoSinNingunCodigo_usaUnCodigoPorDefecto() {
        String cuerpo = evento("payment_intent.payment_failed", """
                {"id":"pi_1","metadata":{"id_reserva":"31"}}""");

        assertThat(leer(cuerpo).codigoRechazo()).isEqualTo("payment_failed");
    }

    @ParameterizedTest(name = "metadatos {0}")
    @ValueSource(strings = {"{}", "{\"id_reserva\":\"abc\"}", "{\"otro\":\"31\"}", "{\"id_reserva\":\"\"}"})
    @DisplayName("Un pago fallido sin una reserva válida en los metadatos no es de este sistema")
    void leerEvento_pagoFallidoSinReservaValida_seIgnora(String metadatos) {
        String cuerpo = evento("payment_intent.payment_failed",
                "{\"id\":\"pi_1\",\"metadata\":" + metadatos + "}");

        assertThat(leer(cuerpo).tipo()).isEqualTo(EventoPasarela.Tipo.IGNORADO);
    }

    @Test
    void leerEvento_deUnTipoQueNoInteresa_seIgnora() {
        String cuerpo = evento("customer.created", "{\"id\":\"cus_1\"}");

        EventoPasarela evento = leer(cuerpo);

        assertThat(evento.tipo()).isEqualTo(EventoPasarela.Tipo.IGNORADO);
        assertThat(evento.idEvento()).isEqualTo("evt_1");
    }

    @Test
    void leerEvento_sinIdentificadorOSinTipo_seIgnora() {
        assertThat(leer("{\"type\":\"checkout.session.expired\",\"data\":{\"object\":{\"id\":\"cs_test_1\"}}}").tipo())
                .isEqualTo(EventoPasarela.Tipo.IGNORADO);
        assertThat(leer("{\"id\":\"evt_1\"}").tipo()).isEqualTo(EventoPasarela.Tipo.IGNORADO);
    }

    @Test
    @DisplayName("Un aviso bien firmado pero que no es JSON se ignora en lugar de fallar")
    void leerEvento_conCuerpoIlegible_seIgnora() {
        EventoPasarela evento = leer("esto no es json {");

        assertThat(evento.tipo()).isEqualTo(EventoPasarela.Tipo.IGNORADO);
        assertThat(evento.idEvento()).isNull();
    }

    // ------------------------------------------------------------------ abrir sesión

    @Test
    @DisplayName("Abre la sesión con el monto en centavos, la reserva en los metadatos y la clave por llamada")
    void crearSesion_enviaLosParametrosEsperados() throws Exception {
        Session creada = new Session();
        creada.setId("cs_test_1");
        creada.setUrl("https://checkout.stripe.test/cs_test_1");
        creada.setExpiresAt(INSTANTE.plusSeconds(35 * 60L).getEpochSecond());
        OffsetDateTime venceEn = OffsetDateTime.ofInstant(INSTANTE.plusSeconds(35 * 60L), ZoneOffset.UTC);

        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(creada);

            SesionCreada sesion = pasarela.crearSesion(new SolicitudSesion(31L, "Reserva 31: Apto",
                    new BigDecimal("875000.00"), "COP", venceEn, "https://app.test/31",
                    "https://app.test/31?cancelado=true"));

            assertThat(sesion.idSesion()).isEqualTo("cs_test_1");
            assertThat(sesion.url()).isEqualTo("https://checkout.stripe.test/cs_test_1");
            assertThat(sesion.venceEn()).isEqualTo(venceEn);

            ArgumentCaptor<SessionCreateParams> parametros = ArgumentCaptor.forClass(SessionCreateParams.class);
            ArgumentCaptor<RequestOptions> opciones = ArgumentCaptor.forClass(RequestOptions.class);
            estatico.verify(() -> Session.create(parametros.capture(), opciones.capture()));

            JsonNode enviado = objectMapper.valueToTree(parametros.getValue().toMap());
            assertThat(enviado.path("mode").asText()).isEqualTo("payment");
            assertThat(enviado.path("success_url").asText()).isEqualTo("https://app.test/31");
            assertThat(enviado.path("cancel_url").asText()).isEqualTo("https://app.test/31?cancelado=true");
            assertThat(enviado.path("client_reference_id").asText()).isEqualTo("31");
            assertThat(enviado.path("expires_at").asLong()).isEqualTo(venceEn.toEpochSecond());
            assertThat(enviado.path("metadata").path("id_reserva").asText()).isEqualTo("31");
            assertThat(enviado.path("payment_intent_data").path("metadata").path("id_reserva").asText())
                    .isEqualTo("31");
            JsonNode linea = enviado.path("line_items").path(0);
            assertThat(linea.path("quantity").asLong()).isEqualTo(1);
            assertThat(linea.path("price_data").path("currency").asText()).isEqualTo("cop");
            assertThat(linea.path("price_data").path("unit_amount").asLong()).isEqualTo(87_500_000L);
            assertThat(linea.path("price_data").path("product_data").path("name").asText())
                    .isEqualTo("Reserva 31: Apto");

            // La clave viaja en la propia llamada, no en el estado global del SDK.
            assertThat(opciones.getValue().getAuthenticator())
                    .isInstanceOfSatisfying(BearerTokenAuthenticator.class,
                            autenticador -> assertThat(autenticador.getApiKey()).isEqualTo("sk_test_clave"));
            assertThat(opciones.getValue().getMaxNetworkRetries()).isPositive();
            assertThat(opciones.getValue().getConnectTimeout()).isPositive();
            assertThat(opciones.getValue().getReadTimeout()).isPositive();
        }
    }

    @Test
    @DisplayName("Si Stripe no responde, abrir la sesión falla con un error de pasarela, sin detalles")
    void crearSesion_siStripeFalla_noEstaDisponible() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenThrow(new ApiConnectionException("sin conexión"));

            assertThatThrownBy(() -> pasarela.crearSesion(solicitud()))
                    .isInstanceOf(PasarelaNoDisponibleException.class)
                    .hasMessageNotContaining("conexión")
                    .hasCauseInstanceOf(ApiConnectionException.class);
        }
    }

    @ParameterizedTest(name = "clave \"{0}\"")
    @NullAndEmptySource
    @DisplayName("Sin clave secreta no se llama a Stripe")
    void crearSesion_sinClave_noEstaDisponible(String clave) {
        StripePasarelaPago sinClave = pasarela(clave, SECRETO);

        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            assertThatThrownBy(() -> sinClave.crearSesion(solicitud()))
                    .isInstanceOf(PasarelaNoDisponibleException.class);

            estatico.verifyNoInteractions();
        }
    }

    private static SolicitudSesion solicitud() {
        return new SolicitudSesion(31L, "Reserva 31", new BigDecimal("875000.00"), "COP",
                OffsetDateTime.ofInstant(INSTANTE.plusSeconds(2100), ZoneOffset.UTC), "https://app.test/31",
                "https://app.test/31?cancelado=true");
    }

    // ------------------------------------------------------------------ consultar sesión

    private static Session sesion(String estado, String estadoPago) {
        Session sesion = new Session();
        sesion.setId("cs_test_1");
        sesion.setStatus(estado);
        sesion.setPaymentStatus(estadoPago);
        sesion.setPaymentIntent("pi_1");
        sesion.setAmountTotal(87_500_000L);
        sesion.setCurrency("cop");
        return sesion;
    }

    @Test
    @DisplayName("Una sesión completa y pagada se informa PAGADA, con lo cobrado")
    void consultarSesion_completaYPagada_esPagada() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_1"), any(RequestOptions.class)))
                    .thenReturn(sesion("complete", "paid"));

            Optional<EstadoSesion> estado = pasarela.consultarSesion("cs_test_1");

            assertThat(estado).isPresent();
            assertThat(estado.get().situacion()).isEqualTo(EstadoSesion.Situacion.PAGADA);
            assertThat(estado.get().idSesion()).isEqualTo("cs_test_1");
            assertThat(estado.get().idPaymentIntent()).isEqualTo("pi_1");
            assertThat(estado.get().monto()).isEqualByComparingTo("875000.00");
            assertThat(estado.get().moneda()).isEqualTo("COP");
        }
    }

    @Test
    void consultarSesion_vencida_esExpirada() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_1"), any(RequestOptions.class)))
                    .thenReturn(sesion("expired", "unpaid"));

            assertThat(pasarela.consultarSesion("cs_test_1")).get()
                    .extracting(EstadoSesion::situacion).isEqualTo(EstadoSesion.Situacion.EXPIRADA);
        }
    }

    @Test
    @DisplayName("Una sesión abierta o completa sin cobrar sigue ABIERTA")
    void consultarSesion_abiertaOSinCobrar_sigueAbierta() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_1"), any(RequestOptions.class)))
                    .thenReturn(sesion("open", "unpaid"), sesion("complete", "unpaid"));

            assertThat(pasarela.consultarSesion("cs_test_1")).get()
                    .extracting(EstadoSesion::situacion).isEqualTo(EstadoSesion.Situacion.ABIERTA);
            assertThat(pasarela.consultarSesion("cs_test_1")).get()
                    .extracting(EstadoSesion::situacion).isEqualTo(EstadoSesion.Situacion.ABIERTA);
        }
    }

    @Test
    void consultarSesion_sinMontoNiMoneda_losDejaNulos() {
        Session sinCobro = sesion("open", "unpaid");
        sinCobro.setAmountTotal(null);
        sinCobro.setCurrency(null);

        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_1"), any(RequestOptions.class)))
                    .thenReturn(sinCobro);

            EstadoSesion estado = pasarela.consultarSesion("cs_test_1").orElseThrow();

            assertThat(estado.monto()).isNull();
            assertThat(estado.moneda()).isNull();
        }
    }

    @Test
    @DisplayName("Una sesión que Stripe no conoce no es un error: no hay nada que sincronizar")
    void consultarSesion_inexistente_devuelveVacio() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_x"), any(RequestOptions.class)))
                    .thenThrow(new InvalidRequestException("No existe", "id", "req_1", "resource_missing", 404, null));

            assertThat(pasarela.consultarSesion("cs_test_x")).isEmpty();
        }
    }

    @Test
    void consultarSesion_conOtroErrorDeSolicitud_noEstaDisponible() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_1"), any(RequestOptions.class)))
                    .thenThrow(new InvalidRequestException("Clave inválida", null, "req_1", "api_key_expired",
                            401, null));

            assertThatThrownBy(() -> pasarela.consultarSesion("cs_test_1"))
                    .isInstanceOf(PasarelaNoDisponibleException.class);
        }
    }

    @Test
    void consultarSesion_siStripeNoResponde_noEstaDisponible() {
        try (MockedStatic<Session> estatico = Mockito.mockStatic(Session.class)) {
            estatico.when(() -> Session.retrieve(eq("cs_test_1"), any(RequestOptions.class)))
                    .thenThrow(new ApiConnectionException("sin conexión"));

            assertThatThrownBy(() -> pasarela.consultarSesion("cs_test_1"))
                    .isInstanceOf(PasarelaNoDisponibleException.class);
        }
    }

    @Test
    void consultarSesion_sinClave_noEstaDisponible() {
        StripePasarelaPago sinClave = pasarela("", SECRETO);

        assertThatThrownBy(() -> sinClave.consultarSesion("cs_test_1"))
                .isInstanceOf(PasarelaNoDisponibleException.class);
    }
}
