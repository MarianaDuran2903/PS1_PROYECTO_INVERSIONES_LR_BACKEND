package com.inversioneslr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.BaseDeDatosDePrueba;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.exception.PasarelaNoDisponibleException;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.ExpiracionReservasService;
import com.inversioneslr.backend.service.PasarelaPago.EstadoSesion;
import com.inversioneslr.backend.service.PasarelaPago.SesionCreada;
import com.inversioneslr.backend.service.PasarelaPago.SolicitudSesion;
import com.inversioneslr.backend.service.TokenService;
import com.inversioneslr.backend.service.impl.StripePasarelaPago;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pago de una reserva con Stripe Checkout en modo de pruebas, de punta a punta con PostgreSQL real. La red nunca
 * se toca: abrir y consultar sesiones están sustituidas, y los avisos (webhooks) se firman aquí con el secreto de
 * pruebas, así que la verificación de firma y la lectura del aviso son las reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PagoIntegrationTest {

    private static final String URL_RESERVAS = "/api/v1/reservas";
    private static final String URL_WEBHOOK = "/api/v1/pagos/webhook";
    /** El mismo secreto de firma de src/test/resources/application.properties. */
    private static final String SECRETO_WEBHOOK = "whsec_secreto_ficticio_de_pruebas";
    private static final String TOTAL_DE_LA_RESERVA = "875000.00";
    private static final long TOTAL_EN_CENTAVOS = 87_500_000L;
    private static final LocalDate BASE = LocalDate.now().plusMonths(6);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;
    private final ExpiracionReservasService expiracionReservasService;

    @SpyBean
    private StripePasarelaPago pasarela;

    private final AtomicInteger sesiones = new AtomicInteger();
    private Usuario anfitrion;
    private Usuario huesped;
    private long idPublicacion;
    private long idReserva;

    @Autowired
    PagoIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                        UsuarioRepository usuarioRepository, TokenService tokenService,
                        ExpiracionReservasService expiracionReservasService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
        this.expiracionReservasService = expiracionReservasService;
    }

    @BeforeEach
    void preparar() throws Exception {
        BaseDeDatosDePrueba.limpiar(jdbcTemplate);
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        huesped = crearUsuario("ana@correo.com", Rol.HUESPED);
        idPublicacion = insertarPublicacion();
        periodo(idPublicacion, dia(0), dia(30));

        // Nada llega a Stripe: se abre una sesión ficticia por llamada y la consulta no sabe de ninguna.
        sesiones.set(0);
        doAnswer(invocacion -> {
            SolicitudSesion solicitud = invocacion.getArgument(0);
            int numero = sesiones.incrementAndGet();
            return new SesionCreada("cs_test_" + numero, "https://checkout.stripe.test/cs_test_" + numero,
                    solicitud.venceEn());
        }).when(pasarela).crearSesion(any());
        doReturn(Optional.empty()).when(pasarela).consultarSesion(anyString());

        idReserva = crearReserva(huesped, "clave-1");
    }

    // ------------------------------------------------------------------ iniciar el pago

    @Test
    @DisplayName("Criterio 1: el Huésped inicia el pago y recibe la página de Stripe de prueba por el total de la reserva")
    void iniciarPago_abreLaSesionPorElTotalDeLaReserva() throws Exception {
        OffsetDateTime antes = OffsetDateTime.now();

        iniciarPago(huesped, idReserva)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPago", notNullValue()))
                .andExpect(jsonPath("$.idReserva").value(idReserva))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.estadoReserva").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.monto").value(875000.00))
                .andExpect(jsonPath("$.moneda").value("COP"))
                .andExpect(jsonPath("$.urlPago", startsWith("https://checkout.stripe.test/")))
                .andExpect(jsonPath("$.fechaExpiracion", notNullValue()))
                .andExpect(jsonPath("$.codigoRechazo").doesNotExist())
                .andExpect(jsonPath("$.motivoRechazo").doesNotExist());

        Map<String, Object> pago = unicoPago();
        assertThat(pago).containsEntry("estado", "PENDIENTE").containsEntry("moneda", "COP")
                .containsEntry("referencia_stripe", "cs_test_1");
        assertThat((BigDecimal) pago.get("monto")).isEqualByComparingTo(TOTAL_DE_LA_RESERVA);
        assertThat(pago.get("url_pago")).isEqualTo("https://checkout.stripe.test/cs_test_1");
        assertThat(pago.get("id_payment_intent")).isNull();

        // La solicitud a Stripe lleva el monto de la reserva y vuelve al front de esa reserva.
        ArgumentCaptor<SolicitudSesion> captor = ArgumentCaptor.forClass(SolicitudSesion.class);
        verify(pasarela).crearSesion(captor.capture());
        SolicitudSesion solicitud = captor.getValue();
        assertThat(solicitud.idReserva()).isEqualTo(idReserva);
        assertThat(solicitud.monto()).isEqualByComparingTo(TOTAL_DE_LA_RESERVA);
        assertThat(solicitud.moneda()).isEqualTo("COP");
        assertThat(solicitud.urlExito()).isEqualTo("http://localhost:4200/reservas/" + idReserva + "/pago/resultado");
        assertThat(solicitud.urlCancelacion()).contains("/reservas/" + idReserva + "/pago/resultado?cancelado=true");
        assertThat(solicitud.descripcion()).contains("Reserva " + idReserva, "Apartamento de prueba");

        // Mientras paga, la reserva no vence: su plazo sube a la sesión (35 min) más la gracia (5 min).
        OffsetDateTime venceReserva = jdbcTemplate.queryForObject(
                "SELECT fecha_expiracion FROM reserva WHERE id_reserva = ?", OffsetDateTime.class, idReserva);
        assertThat(venceReserva).isAfterOrEqualTo(antes.plusMinutes(40).minusSeconds(5));
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'INICIAR_PAGO' AND id_usuario = ?",
                huesped.getIdUsuario())).isEqualTo(1);
    }

    @Test
    @DisplayName("Pedir el pago otra vez devuelve la misma sesión: no se abre otra que también se pudiera pagar")
    void iniciarPago_conSesionAbierta_devuelveLaMisma() throws Exception {
        String primera = respuesta(iniciarPago(huesped, idReserva).andExpect(status().isCreated())).path("urlPago")
                .asText();

        iniciarPago(huesped, idReserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urlPago").value(primera))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));

        assertThat(contar("SELECT COUNT(*) FROM pago_simulado")).isEqualTo(1);
        verify(pasarela, times(1)).crearSesion(any());
    }

    @Test
    @DisplayName("Varias solicitudes a la vez abren una sola sesión")
    void iniciarPago_enParalelo_abreUnaSolaSesion() throws Exception {
        int solicitudes = 5;
        ExecutorService pool = Executors.newFixedThreadPool(solicitudes);
        CountDownLatch salida = new CountDownLatch(1);
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < solicitudes; i++) {
            tareas.add(() -> {
                salida.await();
                return iniciarPago(huesped, idReserva).andReturn().getResponse().getStatus();
            });
        }
        List<Future<Integer>> resultados = new ArrayList<>();
        for (Callable<Integer> tarea : tareas) {
            resultados.add(pool.submit(tarea));
        }
        salida.countDown();
        List<Integer> estados = new ArrayList<>();
        for (Future<Integer> resultado : resultados) {
            estados.add(resultado.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(estados).containsOnlyOnce(201).filteredOn(estado -> estado == 200).hasSize(solicitudes - 1);
        assertThat(contar("SELECT COUNT(*) FROM pago_simulado")).isEqualTo(1);
        verify(pasarela, times(1)).crearSesion(any());
    }

    @Test
    @DisplayName("Si Stripe no responde: 502 sin detalles, ningún pago y la reserva igual que antes")
    void iniciarPago_siStripeFalla_noDejaRastro() throws Exception {
        OffsetDateTime venceAntes = vencimientoDeLaReserva();
        doThrow(new PasarelaNoDisponibleException()).when(pasarela).crearSesion(any());

        iniciarPago(huesped, idReserva)
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.estado").value(502))
                .andExpect(jsonPath("$.mensaje", containsString("pasarela de pagos")));

        assertThat(contar("SELECT COUNT(*) FROM pago_simulado")).isZero();
        assertThat(vencimientoDeLaReserva()).isEqualTo(venceAntes);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'INICIAR_PAGO'")).isZero();
    }

    @Test
    @DisplayName("Una sesión que venció sin aviso se cierra y se abre otra")
    void iniciarPago_conSesionVencidaPorElReloj_abreOtra() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE pago_simulado SET fecha_expiracion = now() - interval '1 minute'");

        iniciarPago(huesped, idReserva)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urlPago").value("https://checkout.stripe.test/cs_test_2"));

        assertThat(estadosDeLosPagos()).containsExactly("EXPIRADO", "PENDIENTE");
    }

    @Test
    @DisplayName("Agotados los intentos de pago no se abren más sesiones")
    void iniciarPago_conIntentosAgotados_responde409() throws Exception {
        for (int i = 1; i <= 3; i++) {
            insertarPago(idReserva, "EXPIRADO", "cs_test_vieja_" + i);
        }

        iniciarPago(huesped, idReserva)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Se agotaron los intentos de pago de esta reserva"));

        verify(pasarela, never()).crearSesion(any());
    }

    @Test
    void iniciarPago_conReservaConfirmada_responde409() throws Exception {
        jdbcTemplate.update("UPDATE reserva SET estado = 'CONFIRMADA' WHERE id_reserva = ?", idReserva);

        iniciarPago(huesped, idReserva)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La reserva ya está pagada y confirmada"));

        verify(pasarela, never()).crearSesion(any());
    }

    @Test
    void iniciarPago_conReservaCancelada_responde409() throws Exception {
        jdbcTemplate.update("UPDATE reserva SET estado = 'CANCELADA' WHERE id_reserva = ?", idReserva);

        iniciarPago(huesped, idReserva)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("venció o ya no admite pago")));

        verify(pasarela, never()).crearSesion(any());
    }

    @Test
    @DisplayName("Una reserva pendiente cuyo plazo ya terminó no admite pago aunque la tarea no la haya vencido")
    void iniciarPago_conPlazoTerminado_responde409() throws Exception {
        jdbcTemplate.update("UPDATE reserva SET fecha_expiracion = now() - interval '1 minute'");

        iniciarPago(huesped, idReserva).andExpect(status().isConflict());

        verify(pasarela, never()).crearSesion(any());
    }

    // ------------------------------------------------------------------ criterio 5: reserva ajena

    @Test
    @DisplayName("Criterio 5: pagar la reserva de otro Huésped se deniega y el intento queda auditado")
    void iniciarPago_conReservaAjena_responde403() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);

        iniciarPago(otro, idReserva)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tiene permisos sobre esta reserva"));

        assertThat(contar("SELECT COUNT(*) FROM pago_simulado")).isZero();
        verify(pasarela, never()).crearSesion(any());
        Map<String, Object> intento = jdbcTemplate.queryForMap(
                "SELECT id_usuario, tipo_entidad, id_entidad, resultado FROM evento_auditoria "
                        + "WHERE accion = 'ACCESO_DENEGADO'");
        assertThat(intento).containsEntry("id_usuario", otro.getIdUsuario())
                .containsEntry("tipo_entidad", "Reserva")
                .containsEntry("id_entidad", String.valueOf(idReserva))
                .containsEntry("resultado", "FALLIDO");
        assertThat(vencimientoDeLaReserva()).isBefore(OffsetDateTime.now().plusMinutes(31));
    }

    @Test
    @DisplayName("Consultar el pago de la reserva de otro Huésped también se deniega")
    void consultarPago_conReservaAjena_responde403() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);

        consultarPago(otro, idReserva).andExpect(status().isForbidden());
    }

    @Test
    void iniciarYConsultar_conUnaReservaQueNoExiste_responde404() throws Exception {
        iniciarPago(huesped, 9999).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La reserva no existe"));
        consultarPago(huesped, 9999).andExpect(status().isNotFound());
    }

    @Test
    void iniciarYConsultar_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL_RESERVAS + "/" + idReserva + "/pago")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL_RESERVAS + "/" + idReserva + "/pago")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"ANFITRION", "ADMINISTRADOR"})
    @DisplayName("Solo el rol Huésped paga")
    void iniciarYConsultar_conOtroRol_responde403(String rol) throws Exception {
        Usuario otroRol = crearUsuario("rol@correo.com", Rol.valueOf(rol));

        iniciarPago(otroRol, idReserva).andExpect(status().isForbidden());
        consultarPago(otroRol, idReserva).andExpect(status().isForbidden());
    }

    @Test
    void iniciarPago_conUnIdentificadorQueNoEsNumero_responde400() throws Exception {
        mockMvc.perform(post(URL_RESERVAS + "/abc/pago").header(HttpHeaders.AUTHORIZATION, token(huesped)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ criterio 2: pago aprobado

    @Test
    @DisplayName("Criterio 2: el pago aprobado confirma la reserva y deja registrado el resultado")
    void aviso_pagoAprobado_confirmaLaReserva() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());

        Map<String, Object> pago = unicoPago();
        assertThat(pago).containsEntry("estado", "APROBADO").containsEntry("id_payment_intent", "pi_1");
        assertThat(pago.get("codigo_rechazo")).isNull();
        assertThat(estadoDeLaReserva()).isEqualTo("CONFIRMADA");
        // Confirmar no toca el calendario: las noches siguen reservadas para esa reserva.
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO' "
                + "AND id_reserva = ?", idReserva)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'APROBAR_PAGO' "
                + "AND tipo_entidad = 'Pago' AND id_usuario IS NULL AND resultado = 'EXITOSO'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CONFIRMAR_RESERVA' "
                + "AND id_entidad = ?", String.valueOf(idReserva))).isEqualTo(1);

        consultarPago(huesped, idReserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"))
                .andExpect(jsonPath("$.urlPago").doesNotExist());
    }

    @Test
    @DisplayName("Criterio 4: queda la evidencia para rastrear el pago: sesión, payment intent y aviso de Stripe")
    void aviso_pagoAprobado_dejaLaEvidenciaDeTrazabilidad() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(completado("evt_traza", "cs_test_1", "pi_traza", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());

        Map<String, Object> pago = unicoPago();
        assertThat(pago).containsEntry("referencia_stripe", "cs_test_1")
                .containsEntry("id_payment_intent", "pi_traza");
        assertThat(((Timestamp) pago.get("fecha_actualizacion")))
                .isAfterOrEqualTo((Timestamp) pago.get("fecha_hora"));
        Map<String, Object> aviso = jdbcTemplate.queryForMap(
                "SELECT id_evento, tipo, id_pago FROM evento_pago_procesado");
        assertThat(aviso).containsEntry("id_evento", "evt_traza").containsEntry("tipo", "PAGO_APROBADO")
                .containsEntry("id_pago", pago.get("id_pago"));
        assertThat(jdbcTemplate.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = 'APROBAR_PAGO'",
                String.class)).contains("pi_traza");
    }

    @Test
    @DisplayName("Un aviso que Stripe reenvía no repite su efecto")
    void aviso_repetido_noRepiteElEfecto() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        String aviso = completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS);

        enviarAviso(aviso).andExpect(status().isOk());
        enviarAviso(aviso).andExpect(status().isOk());

        assertThat(contar("SELECT COUNT(*) FROM evento_pago_procesado")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'APROBAR_PAGO'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CONFIRMAR_RESERVA'")).isEqualTo(1);
        assertThat(estadoDeLaReserva()).isEqualTo("CONFIRMADA");
    }

    // ------------------------------------------------------------------ criterio 3: pago rechazado

    @Test
    @DisplayName("Criterio 3: el pago rechazado se registra, se informa al Huésped y la reserva no se confirma")
    void aviso_pagoRechazado_noConfirmaLaReservaEInforma() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(fallido("evt_2", idReserva, "pi_1", "insufficient_funds")).andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "RECHAZADO")
                .containsEntry("codigo_rechazo", "insufficient_funds")
                .containsEntry("id_payment_intent", "pi_1");
        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'RECHAZAR_PAGO' "
                + "AND detalle LIKE '%insufficient_funds%'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CONFIRMAR_RESERVA'")).isZero();

        consultarPago(huesped, idReserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.estadoReserva").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.codigoRechazo").value("insufficient_funds"))
                .andExpect(jsonPath("$.motivoRechazo").value("La tarjeta no tiene fondos suficientes."))
                // La sesión sigue abierta: el Huésped puede reintentar con otra tarjeta.
                .andExpect(jsonPath("$.urlPago", startsWith("https://checkout.stripe.test/")));
    }

    @Test
    @DisplayName("Tras un rechazo, pedir el pago devuelve la misma sesión para reintentar, no abre otra")
    void iniciarPago_trasUnRechazo_devuelveLaMismaSesion() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        enviarAviso(fallido("evt_2", idReserva, "pi_1", "card_declined")).andExpect(status().isOk());

        iniciarPago(huesped, idReserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.urlPago").value("https://checkout.stripe.test/cs_test_1"));

        assertThat(contar("SELECT COUNT(*) FROM pago_simulado")).isEqualTo(1);
        verify(pasarela, times(1)).crearSesion(any());
    }

    @Test
    @DisplayName("Rechazado y luego aprobado con otra tarjeta: queda aprobado y la reserva confirmada")
    void aviso_rechazadoYLuegoAprobado_confirmaLaReserva() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        enviarAviso(fallido("evt_2", idReserva, "pi_1", "card_declined")).andExpect(status().isOk());

        enviarAviso(completado("evt_3", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "APROBADO");
        assertThat(unicoPago().get("codigo_rechazo")).isNull();
        assertThat(estadoDeLaReserva()).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("Un aviso de fallo que llega tarde no degrada un pago ya aprobado")
    void aviso_fallidoTardio_noDegradaUnPagoAprobado() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        enviarAviso(completado("evt_3", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());

        enviarAviso(fallido("evt_2", idReserva, "pi_0", "card_declined")).andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "APROBADO");
        assertThat(unicoPago().get("codigo_rechazo")).isNull();
        assertThat(estadoDeLaReserva()).isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("Un cobro por un monto distinto al de la reserva no la confirma")
    void aviso_conMontoDistinto_noConfirmaLaReserva() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(completado("evt_1", "cs_test_1", "pi_1", 100_000L)).andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "RECHAZADO").containsEntry("codigo_rechazo",
                "MONTO_NO_COINCIDE");
        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
        consultarPago(huesped, idReserva)
                .andExpect(jsonPath("$.motivoRechazo").value("No se pudo verificar el monto del pago. Contacte a soporte."));
    }

    // ------------------------------------------------------------------ sesión vencida y reserva vencida

    @Test
    @DisplayName("La sesión que vence sin pago queda EXPIRADA y el Huésped puede abrir otra")
    void aviso_sesionExpirada_permiteAbrirOtra() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(expirado("evt_4", "cs_test_1")).andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "EXPIRADO");
        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
        iniciarPago(huesped, idReserva)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.urlPago").value("https://checkout.stripe.test/cs_test_2"));
        assertThat(estadosDeLosPagos()).containsExactly("EXPIRADO", "PENDIENTE");
        consultarPago(huesped, idReserva).andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("Al vencer la reserva su sesión de pago expira; un pago que llega después no la confirma")
    void pagoQueLlegaConLaReservaYaVencida_noLaConfirmaYQuedaAuditado() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE reserva SET fecha_expiracion = now() - interval '1 minute'");

        expiracionReservasService.vencerPendientes();

        assertThat(estadoDeLaReserva()).isEqualTo("CANCELADA");
        assertThat(unicoPago()).containsEntry("estado", "EXPIRADO");
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO'")).isZero();

        enviarAviso(completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "APROBADO").containsEntry("id_payment_intent", "pi_1");
        assertThat(estadoDeLaReserva()).isEqualTo("CANCELADA");
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'PAGO_SIN_RESERVA_ACTIVA'"))
                .isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CONFIRMAR_RESERVA'")).isZero();
    }

    // ------------------------------------------------------------------ firma del aviso

    @Test
    @DisplayName("RNF-SEG-001: un aviso con firma de otro secreto se rechaza y no cambia nada")
    void aviso_conFirmaDeOtroSecreto_seRechaza() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        String aviso = completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS);

        enviarAviso(aviso, firma(aviso, Instant.now().getEpochSecond(), "whsec_otro_secreto"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La firma del aviso no es válida"));

        assertThat(unicoPago()).containsEntry("estado", "PENDIENTE");
        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
        assertThat(contar("SELECT COUNT(*) FROM evento_pago_procesado")).isZero();
    }

    @Test
    void aviso_sinFirma_seRechaza() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS), null)
                .andExpect(status().isBadRequest());

        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("Un aviso con la firma correcta pero modificado después se rechaza")
    void aviso_conElCuerpoAlterado_seRechaza() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        String firmado = completado("evt_1", "cs_test_1", "pi_1", 100L);
        String alterado = completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS);

        enviarAviso(alterado, firma(firmado, Instant.now().getEpochSecond(), SECRETO_WEBHOOK))
                .andExpect(status().isBadRequest());

        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("Un aviso firmado hace mucho no se acepta: no se puede reutilizar una captura")
    void aviso_conFirmaAntigua_seRechaza() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        String aviso = completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS);

        enviarAviso(aviso, firma(aviso, Instant.now().getEpochSecond() - 3600, SECRETO_WEBHOOK))
                .andExpect(status().isBadRequest());

        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
    }

    @Test
    @DisplayName("Un aviso de una sesión que no es nuestra o de un tipo que no usamos responde 200 sin hacer nada")
    void aviso_queNoInteresa_seIgnora() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());

        enviarAviso(completado("evt_9", "cs_test_ajena", "pi_9", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());
        enviarAviso(expirado("evt_10", "cs_test_ajena")).andExpect(status().isOk());
        enviarAviso(fallido("evt_11", 99999, "pi_9", "card_declined")).andExpect(status().isOk());
        enviarAviso(objectMapper.writeValueAsString(Map.of("id", "evt_12", "type", "customer.created",
                "data", Map.of("object", Map.of("id", "cus_1"))))).andExpect(status().isOk());
        enviarAviso("esto no es json {").andExpect(status().isOk());

        assertThat(unicoPago()).containsEntry("estado", "PENDIENTE");
        assertThat(estadoDeLaReserva()).isEqualTo("PENDIENTE_PAGO");
        assertThat(contar("SELECT COUNT(*) FROM evento_pago_procesado")).isZero();
    }

    // ------------------------------------------------------------------ consulta del resultado

    @Test
    @DisplayName("Si el aviso aún no llegó, consultar el pago pregunta a Stripe y confirma la reserva")
    void consultarPago_sinAvisoPeroPagadoEnStripe_confirmaLaReserva() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        doReturn(Optional.of(new EstadoSesion("cs_test_1", EstadoSesion.Situacion.PAGADA, "pi_1",
                new BigDecimal(TOTAL_DE_LA_RESERVA), "COP"))).when(pasarela).consultarSesion("cs_test_1");

        consultarPago(huesped, idReserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"))
                .andExpect(jsonPath("$.estadoReserva").value("CONFIRMADA"));

        assertThat(unicoPago()).containsEntry("estado", "APROBADO").containsEntry("id_payment_intent", "pi_1");
        assertThat(estadoDeLaReserva()).isEqualTo("CONFIRMADA");
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CONFIRMAR_RESERVA'")).isEqualTo(1);
    }

    @Test
    @DisplayName("Si Stripe informa la sesión vencida, consultar la deja EXPIRADA")
    void consultarPago_conSesionVencidaEnStripe_laExpira() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        doReturn(Optional.of(new EstadoSesion("cs_test_1", EstadoSesion.Situacion.EXPIRADA, null, null, null)))
                .when(pasarela).consultarSesion("cs_test_1");

        consultarPago(huesped, idReserva)
                .andExpect(jsonPath("$.estado").value("EXPIRADO"))
                .andExpect(jsonPath("$.urlPago").doesNotExist());
    }

    @Test
    @DisplayName("Si Stripe no responde, consultar el pago no falla: devuelve lo que hay")
    void consultarPago_siStripeNoResponde_devuelveElEstadoActual() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        doThrow(new PasarelaNoDisponibleException()).when(pasarela).consultarSesion(anyString());

        consultarPago(huesped, idReserva)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.urlPago", notNullValue()));
    }

    @Test
    void consultarPago_conReservaSinPago_responde404() throws Exception {
        consultarPago(huesped, idReserva)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La reserva no tiene un pago iniciado"));
    }

    @Test
    @DisplayName("Consultar un pago que ya terminó no vuelve a preguntar a Stripe")
    void consultarPago_conPagoAprobado_noPreguntaAStripe() throws Exception {
        iniciarPago(huesped, idReserva).andExpect(status().isCreated());
        enviarAviso(completado("evt_1", "cs_test_1", "pi_1", TOTAL_EN_CENTAVOS)).andExpect(status().isOk());

        consultarPago(huesped, idReserva).andExpect(jsonPath("$.estado").value("APROBADO"));

        verify(pasarela, never()).consultarSesion(anyString());
    }

    // ------------------------------------------------------------------ restricciones de la base (V10)

    @Test
    @DisplayName("V10: una reserva no puede tener dos sesiones abiertas, ni siquiera una pendiente y una rechazada")
    void baseDeDatos_noPermiteDosSesionesAbiertasPorReserva() {
        insertarPago(idReserva, "PENDIENTE", "cs_test_a");

        assertThatThrownBy(() -> insertarPago(idReserva, "PENDIENTE", "cs_test_b"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_pago_abierto_por_reserva");
        assertThatThrownBy(() -> insertarPago(idReserva, "RECHAZADO", "cs_test_c"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_pago_abierto_por_reserva");
        // Las sesiones que terminaron no cuentan: pueden coexistir con una abierta.
        insertarPago(idReserva, "EXPIRADO", "cs_test_d");
        insertarPago(idReserva, "APROBADO", "cs_test_e");
        assertThat(contar("SELECT COUNT(*) FROM pago_simulado")).isEqualTo(3);
    }

    @Test
    void baseDeDatos_exigeUnEstadoValidoYUnaSesion() {
        assertThatThrownBy(() -> insertarPago(idReserva, "INVENTADO", "cs_test_a"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_pago_estado");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO pago_simulado (id_reserva, monto, estado, referencia_stripe)
                VALUES (?, 100, 'EXPIRADO', NULL)
                """, idReserva)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void baseDeDatos_noRepiteLaSesionNiElAviso() {
        insertarPago(idReserva, "EXPIRADO", "cs_test_a");
        Long idPago = jdbcTemplate.queryForObject("SELECT id_pago FROM pago_simulado", Long.class);

        assertThatThrownBy(() -> insertarPago(idReserva, "EXPIRADO", "cs_test_a"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_pago_referencia_stripe");

        jdbcTemplate.update("INSERT INTO evento_pago_procesado (id_evento, tipo, id_pago) VALUES ('evt_1', 'X', ?)",
                idPago);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO evento_pago_procesado (id_evento, tipo, id_pago) VALUES ('evt_1', 'X', ?)", idPago))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pk_evento_pago_procesado");
    }

    // ------------------------------------------------------------------ apoyo

    private static LocalDate dia(int desdeLaBase) {
        return BASE.plusDays(desdeLaBase);
    }

    private ResultActions iniciarPago(Usuario quien, long reserva) throws Exception {
        return mockMvc.perform(post(URL_RESERVAS + "/" + reserva + "/pago")
                .header(HttpHeaders.AUTHORIZATION, token(quien)));
    }

    private ResultActions consultarPago(Usuario quien, long reserva) throws Exception {
        return mockMvc.perform(get(URL_RESERVAS + "/" + reserva + "/pago")
                .header(HttpHeaders.AUTHORIZATION, token(quien)));
    }

    private long crearReserva(Usuario quien, String clave) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idPublicacion", idPublicacion);
        cuerpo.put("fechaLlegada", dia(10).toString());
        cuerpo.put("fechaSalida", dia(13).toString());
        cuerpo.put("huespedes", 2);
        MockHttpServletResponse respuesta = mockMvc.perform(post(URL_RESERVAS)
                        .header(HttpHeaders.AUTHORIZATION, token(quien))
                        .header("Idempotency-Key", clave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse();
        return objectMapper.readTree(respuesta.getContentAsString()).path("idReserva").asLong();
    }

    private JsonNode respuesta(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    /** Un aviso firmado como lo hace Stripe, con la hora actual. */
    private ResultActions enviarAviso(String cuerpo) throws Exception {
        return enviarAviso(cuerpo, firma(cuerpo, Instant.now().getEpochSecond(), SECRETO_WEBHOOK));
    }

    private ResultActions enviarAviso(String cuerpo, String encabezadoFirma) throws Exception {
        MockHttpServletRequestBuilder solicitud = post(URL_WEBHOOK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo.getBytes(StandardCharsets.UTF_8));
        if (encabezadoFirma != null) {
            solicitud.header("Stripe-Signature", encabezadoFirma);
        }
        return mockMvc.perform(solicitud);
    }

    /** La firma de Stripe: HMAC-SHA256 del texto "marca.cuerpo" con el secreto del webhook. */
    private static String firma(String cuerpo, long marca, String secreto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] resultado = mac.doFinal((marca + "." + cuerpo).getBytes(StandardCharsets.UTF_8));
            return "t=" + marca + ",v1=" + HexFormat.of().formatHex(resultado);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String completado(String idEvento, String idSesion, String idPaymentIntent, long centavos)
            throws Exception {
        Map<String, Object> sesion = new LinkedHashMap<>();
        sesion.put("id", idSesion);
        sesion.put("payment_status", "paid");
        sesion.put("payment_intent", idPaymentIntent);
        sesion.put("amount_total", centavos);
        sesion.put("currency", "cop");
        return evento(idEvento, "checkout.session.completed", sesion);
    }

    private String fallido(String idEvento, long reserva, String idPaymentIntent, String codigoDelBanco)
            throws Exception {
        Map<String, Object> intento = new LinkedHashMap<>();
        intento.put("id", idPaymentIntent);
        intento.put("metadata", Map.of("id_reserva", String.valueOf(reserva)));
        intento.put("last_payment_error", Map.of("code", "card_declined", "decline_code", codigoDelBanco));
        return evento(idEvento, "payment_intent.payment_failed", intento);
    }

    private String expirado(String idEvento, String idSesion) throws Exception {
        return evento(idEvento, "checkout.session.expired", Map.of("id", idSesion));
    }

    private String evento(String idEvento, String tipo, Map<String, Object> objeto) throws Exception {
        return objectMapper.writeValueAsString(Map.of("id", idEvento, "type", tipo, "data", Map.of("object", objeto)));
    }

    private String token(Usuario usuario) {
        return "Bearer " + tokenService.generar(usuario).token();
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.save(new Usuario(new NombreCompleto("Nombre", null, "Apellido", null),
                correo, "hash-no-usado", rol));
    }

    private long insertarPublicacion() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO publicacion (id_anfitrion, id_ciudad, id_tipo, id_politica, titulo, descripcion,
                                         direccion_textual, capacidad, precio_noche, tarifa_limpieza, reglas, estado)
                VALUES (?, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                        (SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'),
                        (SELECT id_politica FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        'Apartamento de prueba', 'Descripción de prueba', 'Calle 5 # 3-20', 4, 250000.00,
                        50000.00, 'Reglas de prueba', 'ACTIVA')
                RETURNING id_publicacion
                """, Long.class, anfitrion.getIdUsuario());
    }

    private void periodo(long idDePublicacion, LocalDate inicio, LocalDate fin) {
        jdbcTemplate.update("""
                INSERT INTO periodo_disponibilidad (id_publicacion, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, 'DISPONIBLE')
                """, idDePublicacion, Date.valueOf(inicio), Date.valueOf(fin));
    }

    private void insertarPago(long reserva, String estado, String referencia) {
        jdbcTemplate.update("""
                INSERT INTO pago_simulado (id_reserva, monto, estado, referencia_stripe, fecha_expiracion)
                VALUES (?, 875000.00, ?, ?, now() + interval '10 minutes')
                """, reserva, estado, referencia);
    }

    private Map<String, Object> unicoPago() {
        return jdbcTemplate.queryForMap("SELECT * FROM pago_simulado");
    }

    private List<String> estadosDeLosPagos() {
        return jdbcTemplate.queryForList("SELECT estado FROM pago_simulado ORDER BY id_pago", String.class);
    }

    private String estadoDeLaReserva() {
        return jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class,
                idReserva);
    }

    private OffsetDateTime vencimientoDeLaReserva() {
        return jdbcTemplate.queryForObject("SELECT fecha_expiracion FROM reserva WHERE id_reserva = ?",
                OffsetDateTime.class, idReserva);
    }

    private int contar(String sql, Object... parametros) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class, parametros);
        return total == null ? 0 : total;
    }
}
