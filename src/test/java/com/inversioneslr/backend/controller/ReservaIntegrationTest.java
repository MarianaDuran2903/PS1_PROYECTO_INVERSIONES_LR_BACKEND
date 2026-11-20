package com.inversioneslr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.BaseDeDatosDePrueba;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reserva de un alojamiento por un Huésped: precio, calendario, idempotencia y doble reserva. PostgreSQL real con
 * las migraciones, porque las garantías centrales son restricciones de la base de datos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReservaIntegrationTest {

    private static final String URL = "/api/v1/reservas";
    private static final String ENCABEZADO_CLAVE = "Idempotency-Key";
    /** Las fechas parten de seis meses adelante para no depender del día en que corre la prueba. */
    private static final LocalDate BASE = LocalDate.now().plusMonths(6);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario huesped;
    private long idPublicacion;

    @Autowired
    ReservaIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                           UsuarioRepository usuarioRepository, TokenService tokenService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    @BeforeEach
    void preparar() {
        BaseDeDatosDePrueba.limpiar(jdbcTemplate);
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        huesped = crearUsuario("ana@correo.com", Rol.HUESPED);
        idPublicacion = insertarPublicacion("ACTIVA");
        periodo(idPublicacion, dia(0), dia(30), "DISPONIBLE");
    }

    // ------------------------------------------------------------------ creación

    @Test
    @DisplayName("Un Huésped reserva: queda PENDIENTE_PAGO con el precio, la política y el vencimiento")
    void reservar_conCalendarioDisponible_creaLaReservaPendienteDePago() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idReserva", notNullValue()))
                .andExpect(jsonPath("$.idPublicacion").value(idPublicacion))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.fechaLlegada").value(dia(10).toString()))
                .andExpect(jsonPath("$.fechaSalida").value(dia(13).toString()))
                .andExpect(jsonPath("$.noches").value(3))
                .andExpect(jsonPath("$.huespedes").value(2))
                .andExpect(jsonPath("$.fechaExpiracion", notNullValue()))
                .andExpect(jsonPath("$.precio.precioBase").value(750000.00))
                .andExpect(jsonPath("$.precio.porcentajeDescuento").value(0))
                .andExpect(jsonPath("$.precio.valorDescuento").value(0))
                .andExpect(jsonPath("$.precio.tarifaLimpieza").value(50000.00))
                .andExpect(jsonPath("$.precio.porcentajeTarifaServicio").value(10.00))
                .andExpect(jsonPath("$.precio.tarifaServicio").value(75000.00))
                .andExpect(jsonPath("$.precio.total").value(875000.00))
                .andExpect(jsonPath("$.politicaCancelacion.horasAnticipacionMin").value(24))
                .andExpect(jsonPath("$.politicaCancelacion.porcentajeDevolucion").value(100.00));

        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT id_huesped, id_publicacion, estado, clave_idempotencia, numero_huespedes, total,
                       horas_anticipacion_aplicada, porcentaje_devolucion_aplicado
                FROM reserva
                """);
        assertThat(fila)
                .containsEntry("id_huesped", huesped.getIdUsuario())
                .containsEntry("id_publicacion", idPublicacion)
                .containsEntry("estado", "PENDIENTE_PAGO")
                .containsEntry("clave_idempotencia", "clave-1")
                .containsEntry("numero_huespedes", 2)
                .containsEntry("horas_anticipacion_aplicada", 24);
        assertThat((BigDecimal) fila.get("total")).isEqualByComparingTo("875000.00");
    }

    @Test
    @DisplayName("El plazo para pagar es la vigencia configurada, contada desde ahora")
    void reservar_fijaElVencimientoSegunLaVigenciaConfigurada() throws Exception {
        OffsetDateTime antes = OffsetDateTime.now();

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        OffsetDateTime vence = jdbcTemplate.queryForObject("SELECT fecha_expiracion FROM reserva",
                OffsetDateTime.class);
        assertThat(vence).isAfterOrEqualTo(antes.plusMinutes(30).minusSeconds(1));
        assertThat(vence).isBeforeOrEqualTo(OffsetDateTime.now().plusMinutes(30).plusSeconds(1));
    }

    @Test
    @DisplayName("Una estadía de 7 noches o más recibe el descuento configurado")
    void reservar_conEstadiaLarga_aplicaElDescuento() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(17), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noches").value(7))
                .andExpect(jsonPath("$.precio.precioBase").value(1750000.00))
                .andExpect(jsonPath("$.precio.porcentajeDescuento").value(10.00))
                .andExpect(jsonPath("$.precio.valorDescuento").value(175000.00))
                .andExpect(jsonPath("$.precio.tarifaServicio").value(157500.00))
                .andExpect(jsonPath("$.precio.total").value(1782500.00));
    }

    @Test
    @DisplayName("Seis noches todavía no reciben descuento")
    void reservar_conSeisNoches_noAplicaElDescuento() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(16), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noches").value(6))
                .andExpect(jsonPath("$.precio.porcentajeDescuento").value(0))
                .andExpect(jsonPath("$.precio.total").value(1500000.00 + 50000.00 + 150000.00));
    }

    @Test
    @DisplayName("El calendario se divide: lo que sobra sigue disponible y las noches reservadas se marcan")
    void reservar_dividePeriodoDisponible() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        Long idReserva = jdbcTemplate.queryForObject("SELECT id_reserva FROM reserva", Long.class);
        List<Map<String, Object>> periodos = periodosDeLaPublicacion();
        assertThat(periodos).hasSize(3);
        assertPeriodo(periodos.get(0), dia(0), dia(10), "DISPONIBLE", null);
        assertPeriodo(periodos.get(1), dia(10), dia(13), "RESERVADO", idReserva);
        assertPeriodo(periodos.get(2), dia(13), dia(30), "DISPONIBLE", null);
    }

    @Test
    @DisplayName("Una estadía que cruza dos tramos contiguos los reemplaza a ambos")
    void reservar_cruzandoDosTramosContiguos_loscubreAmbos() throws Exception {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        periodo(idPublicacion, dia(0), dia(11), "DISPONIBLE");
        periodo(idPublicacion, dia(11), dia(30), "DISPONIBLE");

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        Long idReserva = jdbcTemplate.queryForObject("SELECT id_reserva FROM reserva", Long.class);
        List<Map<String, Object>> periodos = periodosDeLaPublicacion();
        assertThat(periodos).hasSize(3);
        assertPeriodo(periodos.get(0), dia(0), dia(10), "DISPONIBLE", null);
        assertPeriodo(periodos.get(1), dia(10), dia(13), "RESERVADO", idReserva);
        assertPeriodo(periodos.get(2), dia(13), dia(30), "DISPONIBLE", null);
    }

    @Test
    @DisplayName("Un tramo exacto queda entero como reservado")
    void reservar_conTramoExacto_loDejaReservado() throws Exception {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        periodo(idPublicacion, dia(10), dia(13), "DISPONIBLE");

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        List<Map<String, Object>> periodos = periodosDeLaPublicacion();
        assertThat(periodos).hasSize(1);
        assertThat(periodos.get(0)).containsEntry("tipo", "RESERVADO");
    }

    @Test
    @DisplayName("La creación deja el evento de auditoría del Huésped")
    void reservar_registraLaAuditoria() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        Long idReserva = jdbcTemplate.queryForObject("SELECT id_reserva FROM reserva", Long.class);
        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT id_usuario, accion, tipo_entidad, id_entidad, resultado
                FROM evento_auditoria WHERE accion = 'CREAR_RESERVA'
                """);
        assertThat(evento)
                .containsEntry("id_usuario", huesped.getIdUsuario())
                .containsEntry("tipo_entidad", "Reserva")
                .containsEntry("id_entidad", String.valueOf(idReserva))
                .containsEntry("resultado", "EXITOSO");
    }

    @Test
    @DisplayName("Las reservas pegadas, con la salida de una el día de llegada de la otra, conviven")
    void reservar_conLlegadaElDiaDeSalidaDeOtra_sePermite() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        reservar(otro, "clave-2", cuerpo(dia(13), dia(15), 2)).andExpect(status().isCreated());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(2);
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO'")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ idempotencia

    @Test
    @DisplayName("La misma clave con la misma solicitud devuelve la misma reserva y no crea otra")
    void reservar_repetidoConLaMismaClave_devuelveLaMismaReserva() throws Exception {
        String primera = reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long idReserva = objectMapper.readTree(primera).get("idReserva").asLong();

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva").value(idReserva))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
                .andExpect(jsonPath("$.precio.total").value(875000.00));

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(1);
        assertThat(periodosDeLaPublicacion()).hasSize(3);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CREAR_RESERVA'")).isEqualTo(1);
    }

    @Test
    @DisplayName("La misma clave con otra solicitud responde 409 y no cambia nada")
    void reservar_conLaMismaClaveYOtraSolicitud_responde409() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(14), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("clave de idempotencia")));

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(1);
    }

    @Test
    @DisplayName("La clave es por Huésped: otro Huésped puede usar la misma clave")
    void reservar_conLaMismaClaveDeOtroHuesped_noChoca() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        reservar(otro, "clave-1", cuerpo(dia(20), dia(22), 2)).andExpect(status().isCreated());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(2);
    }

    @Test
    @DisplayName("Reintentar tras vencer la reserva devuelve esa misma reserva, ya CANCELADA")
    void reservar_repetidoDespuesDeVencer_devuelveLaReservaCancelada() throws Exception {
        String primera = reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2))
                .andReturn().getResponse().getContentAsString();
        long idReserva = objectMapper.readTree(primera).get("idReserva").asLong();
        jdbcTemplate.update("UPDATE reserva SET estado = 'CANCELADA' WHERE id_reserva = ?", idReserva);

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idReserva").value(idReserva))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    @DisplayName("El mismo Huésped con la misma clave, a la vez, obtiene una sola reserva")
    void reservar_conLaMismaClaveEnParalelo_creaUnaSolaReserva() throws Exception {
        int solicitudes = 5;
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < solicitudes; i++) {
            tareas.add(() -> reservar(huesped, "clave-paralela", cuerpo(dia(10), dia(13), 2))
                    .andReturn().getResponse().getStatus());
        }

        List<Integer> estados = ejecutarEnParalelo(tareas);

        assertThat(estados).filteredOn(estado -> estado == 201).hasSize(1);
        assertThat(estados).filteredOn(estado -> estado == 200).hasSize(solicitudes - 1);
        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO'")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ doble reserva

    @Test
    @DisplayName("Noches que se cruzan con una reserva existente responden 409")
    void reservar_conNochesOcupadas_responde409() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        reservar(otro, "clave-2", cuerpo(dia(12), dia(15), 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("no están disponibles")));
        reservar(otro, "clave-3", cuerpo(dia(10), dia(13), 2)).andExpect(status().isConflict());
        reservar(otro, "clave-4", cuerpo(dia(11), dia(12), 2)).andExpect(status().isConflict());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(1);
    }

    @Test
    @DisplayName("Varios Huéspedes piden las mismas noches a la vez: solo uno las obtiene")
    void reservar_variosHuespedesLasMismasNoches_soloUnoLasObtiene() throws Exception {
        int huespedes = 6;
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < huespedes; i++) {
            Usuario competidor = crearUsuario("competidor" + i + "@correo.com", Rol.HUESPED);
            tareas.add(() -> reservar(competidor, "clave-" + competidor.getIdUsuario(),
                    cuerpo(dia(10), dia(13), 2)).andReturn().getResponse().getStatus());
        }

        List<Integer> estados = ejecutarEnParalelo(tareas);

        assertThat(estados).filteredOn(estado -> estado == 201).hasSize(1);
        assertThat(estados).filteredOn(estado -> estado == 409).hasSize(huespedes - 1);
        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO'")).isEqualTo(1);
        assertThat(periodosDeLaPublicacion()).hasSize(3);
    }

    @Test
    @DisplayName("Dos Huéspedes con noches distintas del mismo tramo grande reservan sin estorbarse")
    void reservar_enParaleloNochesDistintasDelMismoTramo_ambosReservan() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        List<Callable<Integer>> tareas = List.of(
                () -> reservar(huesped, "clave-a", cuerpo(dia(5), dia(8), 2)).andReturn().getResponse().getStatus(),
                () -> reservar(otro, "clave-b", cuerpo(dia(20), dia(23), 2)).andReturn().getResponse().getStatus());

        List<Integer> estados = ejecutarEnParalelo(tareas);

        assertThat(estados).containsExactly(201, 201);
        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(2);
        assertThat(periodosDeLaPublicacion()).hasSize(5);
    }

    @Test
    @DisplayName("La base rechaza la doble reserva aunque el calendario no la refleje, y no deja nada a medias")
    void reservar_conReservaQueElCalendarioNoRefleja_responde409YRevierte() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        insertarReserva(otro, dia(11), dia(12), "CONFIRMADA", "clave-externa");

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isConflict());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(1);
        List<Map<String, Object>> periodos = periodosDeLaPublicacion();
        assertThat(periodos).hasSize(1);
        assertPeriodo(periodos.get(0), dia(0), dia(30), "DISPONIBLE", null);
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria WHERE accion = 'CREAR_RESERVA'")).isZero();
    }

    // ------------------------------------------------------------------ calendario

    @Test
    @DisplayName("Sin calendario disponible para las noches pedidas responde 409")
    void reservar_sinCalendarioParaLasNoches_responde409() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(40), dia(42), 2))
                .andExpect(status().isConflict());
        reservar(huesped, "clave-2", cuerpo(dia(28), dia(32), 2))
                .andExpect(status().isConflict());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
        assertThat(periodosDeLaPublicacion()).hasSize(1);
    }

    @Test
    @DisplayName("Un hueco o una noche bloqueada en medio impiden reservar")
    void reservar_conHuecoONocheBloqueada_responde409() throws Exception {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        periodo(idPublicacion, dia(0), dia(11), "DISPONIBLE");
        periodo(idPublicacion, dia(11), dia(12), "BLOQUEADO");
        periodo(idPublicacion, dia(12), dia(30), "DISPONIBLE");

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isConflict());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
        assertThat(periodosDeLaPublicacion()).hasSize(3);
    }

    // ------------------------------------------------------------------ validaciones

    @Test
    @DisplayName("Sin la clave de idempotencia responde 400")
    void reservar_sinClave_responde400() throws Exception {
        mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, token(huesped))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia(10), dia(13), 2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['Idempotency-Key']", notNullValue()));

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
    }

    @ParameterizedTest(name = "clave [{0}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Una clave en blanco responde 400")
    void reservar_conClaveEnBlanco_responde400(String clave) throws Exception {
        reservar(huesped, clave, cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['Idempotency-Key']", notNullValue()));
    }

    @Test
    @DisplayName("Una clave de más de 100 caracteres responde 400")
    void reservar_conClaveDemasiadoLarga_responde400() throws Exception {
        reservar(huesped, "k".repeat(101), cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['Idempotency-Key']", notNullValue()));

        reservar(huesped, "k".repeat(100), cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Sin token responde 401")
    void reservar_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL)
                        .header(ENCABEZADO_CLAVE, "clave-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia(10), dia(13), 2)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"ANFITRION", "ADMINISTRADOR"})
    @DisplayName("Solo el rol Huésped reserva")
    void reservar_conOtroRol_responde403(String rol) throws Exception {
        Usuario otroRol = crearUsuario("rol@correo.com", Rol.valueOf(rol));

        reservar(otroRol, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isForbidden());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
    }

    @Test
    @DisplayName("Llegar antes de hoy responde 400 en el campo de la llegada")
    void reservar_conLlegadaPasada_responde400() throws Exception {
        reservar(huesped, "clave-1", cuerpo(LocalDate.now().minusDays(1), dia(13), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaLlegada", containsString("anterior a hoy")));
    }

    @Test
    @DisplayName("Más huéspedes que la capacidad responde 400 en el campo de huéspedes")
    void reservar_conMasHuespedesQueLaCapacidad_responde400() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.huespedes", containsString("4")));

        reservar(huesped, "clave-2", cuerpo(dia(10), dia(13), 4)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Una salida igual o anterior a la llegada responde 400")
    void reservar_conSalidaNoPosterior_responde400() throws Exception {
        reservar(huesped, "clave-1", cuerpo(dia(10), dia(10), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.rangoFechasValido", notNullValue()));
        reservar(huesped, "clave-2", cuerpo(dia(10), dia(9), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.rangoFechasValido", notNullValue()));
    }

    @Test
    @DisplayName("Los campos obligatorios ausentes o inválidos responden 400 con cada error")
    void reservar_conDatosIncompletos_responde400() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("huespedes", 0);

        reservar(huesped, "clave-1", objectMapper.writeValueAsString(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idPublicacion", notNullValue()))
                .andExpect(jsonPath("$.errores.fechaLlegada", notNullValue()))
                .andExpect(jsonPath("$.errores.fechaSalida", notNullValue()))
                .andExpect(jsonPath("$.errores.huespedes", notNullValue()));

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
    }

    @ParameterizedTest(name = "publicación {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Una publicación que no está ACTIVA no se puede reservar")
    void reservar_conPublicacionNoActiva_responde404(String estado) throws Exception {
        jdbcTemplate.update("UPDATE publicacion SET estado = ? WHERE id_publicacion = ?", estado, idPublicacion);

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El alojamiento no está disponible"));

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
    }

    @Test
    @DisplayName("Una publicación inexistente responde 404")
    void reservar_conPublicacionInexistente_responde404() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idPublicacion", 999999);
        cuerpo.put("fechaLlegada", dia(10).toString());
        cuerpo.put("fechaSalida", dia(13).toString());
        cuerpo.put("huespedes", 2);

        reservar(huesped, "clave-1", objectMapper.writeValueAsString(cuerpo)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ vencimiento al reservar

    @Test
    @DisplayName("Una reserva pendiente ya vencida no estorba: se vence y las noches pasan a quien las pide")
    void reservar_sobreNochesDeUnaReservaVencida_laVenceYReserva() throws Exception {
        Usuario moroso = crearUsuario("moroso@correo.com", Rol.HUESPED);
        long vencida = insertarReservaPendienteConTramo(moroso, dia(10), dia(13), "clave-moroso",
                OffsetDateTime.now().minusMinutes(5));

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isCreated());

        assertThat(estadoDe(vencida)).isEqualTo("CANCELADA");
        assertThat(contar("SELECT COUNT(*) FROM reserva WHERE estado = 'PENDIENTE_PAGO'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE tipo = 'RESERVADO'")).isEqualTo(1);
        assertThat(contar("SELECT COUNT(*) FROM periodo_disponibilidad WHERE id_reserva = " + vencida)).isZero();
        Map<String, Object> evento = jdbcTemplate.queryForMap(
                "SELECT id_usuario, resultado FROM evento_auditoria WHERE accion = 'VENCER_RESERVA'");
        assertThat(evento.get("id_usuario")).isNull();
        assertThat(evento).containsEntry("resultado", "EXITOSO");
    }

    @Test
    @DisplayName("Una reserva pendiente que aún está dentro de su plazo sigue ocupando las noches")
    void reservar_sobreNochesDeUnaReservaVigente_responde409() throws Exception {
        Usuario pagando = crearUsuario("pagando@correo.com", Rol.HUESPED);
        long vigente = insertarReservaPendienteConTramo(pagando, dia(10), dia(13), "clave-pagando",
                OffsetDateTime.now().plusMinutes(20));

        reservar(huesped, "clave-1", cuerpo(dia(10), dia(13), 2)).andExpect(status().isConflict());

        assertThat(estadoDe(vigente)).isEqualTo("PENDIENTE_PAGO");
    }

    // ------------------------------------------------------------------ restricciones de la base (V8)

    @Test
    @DisplayName("La base exige fecha de vencimiento en toda reserva PENDIENTE_PAGO")
    void base_rechazaPendienteDePagoSinVencimiento() {
        assertThatThrownBy(() -> jdbcTemplate.update(sqlInsertarReserva("PENDIENTE_PAGO", "NULL"),
                huesped.getIdUsuario(), idPublicacion, Date.valueOf(dia(10)), Date.valueOf(dia(13)),
                "sin-vencimiento"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_reserva_expiracion");
    }

    @Test
    @DisplayName("Una reserva PENDIENTE_PAGO ocupa las fechas; una CANCELADA no")
    void base_pendienteDePagoOcupaFechasYCanceladaNo() {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        insertarReserva(huesped, dia(10), dia(13), "PENDIENTE_PAGO", "clave-1");

        assertThatThrownBy(() -> insertarReserva(otro, dia(12), dia(14), "CONFIRMADA", "clave-2"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_reserva_sin_solape");

        jdbcTemplate.update("UPDATE reserva SET estado = 'CANCELADA'");
        insertarReserva(otro, dia(12), dia(14), "CONFIRMADA", "clave-3");
        assertThat(contar("SELECT COUNT(*) FROM reserva")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ apoyo

    private static LocalDate dia(int desdeLaBase) {
        return BASE.plusDays(desdeLaBase);
    }

    private ResultActions reservar(Usuario quien, String clave, String cuerpoJson) throws Exception {
        return mockMvc.perform(post(URL)
                .header(HttpHeaders.AUTHORIZATION, token(quien))
                .header(ENCABEZADO_CLAVE, clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson));
    }

    private String cuerpo(LocalDate llegada, LocalDate salida, int huespedes) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idPublicacion", idPublicacion);
        cuerpo.put("fechaLlegada", llegada.toString());
        cuerpo.put("fechaSalida", salida.toString());
        cuerpo.put("huespedes", huespedes);
        return objectMapper.writeValueAsString(cuerpo);
    }

    private String token(Usuario usuario) {
        return "Bearer " + tokenService.generar(usuario).token();
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.save(new Usuario(new NombreCompleto("Nombre", null, "Apellido", null),
                correo, "hash-no-usado", rol));
    }

    private long insertarPublicacion(String estado) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO publicacion (id_anfitrion, id_ciudad, id_tipo, id_politica, titulo, descripcion,
                                         direccion_textual, capacidad, precio_noche, tarifa_limpieza, reglas, estado)
                VALUES (?, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                        (SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'),
                        (SELECT id_politica FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        'Apartamento de prueba', 'Descripción de prueba', 'Calle 5 # 3-20', 4, 250000.00,
                        50000.00, 'Reglas de prueba', ?)
                RETURNING id_publicacion
                """, Long.class, anfitrion.getIdUsuario(), estado);
    }

    private void periodo(long idDePublicacion, LocalDate inicio, LocalDate fin, String tipo) {
        jdbcTemplate.update("""
                INSERT INTO periodo_disponibilidad (id_publicacion, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, ?)
                """, idDePublicacion, Date.valueOf(inicio), Date.valueOf(fin), tipo);
    }

    private static String sqlInsertarReserva(String estado, String vencimiento) {
        return """
                INSERT INTO reserva (id_huesped, id_publicacion, fecha_llegada, fecha_salida, estado,
                                     clave_idempotencia, precio_base, porcentaje_descuento_aplicado, valor_descuento,
                                     tarifa_limpieza, porcentaje_tarifa_servicio, tarifa_servicio, total,
                                     horas_anticipacion_aplicada, porcentaje_devolucion_aplicado, fecha_expiracion)
                VALUES (?, ?, ?, ?, '%s', ?, 100000, 0, 0, 0, 0, 0, 100000, 24, 100, %s)
                """.formatted(estado, vencimiento);
    }

    private long insertarReserva(Usuario quien, LocalDate llegada, LocalDate salida, String estado, String clave) {
        String vencimiento = "PENDIENTE_PAGO".equals(estado) ? "now() + interval '30 minutes'" : "NULL";
        jdbcTemplate.update(sqlInsertarReserva(estado, vencimiento), quien.getIdUsuario(), idPublicacion,
                Date.valueOf(llegada), Date.valueOf(salida), clave);
        return jdbcTemplate.queryForObject("SELECT id_reserva FROM reserva WHERE clave_idempotencia = ?",
                Long.class, clave);
    }

    /**
     * Una reserva pendiente con su tramo reservado, como la dejaría el flujo normal: el tramo disponible grande
     * queda partido alrededor de las noches reservadas.
     */
    private long insertarReservaPendienteConTramo(Usuario quien, LocalDate llegada, LocalDate salida, String clave,
                                                  OffsetDateTime vence) {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        long idReserva = insertarReserva(quien, llegada, salida, "PENDIENTE_PAGO", clave);
        jdbcTemplate.update("UPDATE reserva SET fecha_expiracion = ? WHERE id_reserva = ?", vence, idReserva);
        periodo(idPublicacion, dia(0), llegada, "DISPONIBLE");
        jdbcTemplate.update("""
                INSERT INTO periodo_disponibilidad (id_publicacion, id_reserva, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, ?, 'RESERVADO')
                """, idPublicacion, idReserva, Date.valueOf(llegada), Date.valueOf(salida));
        periodo(idPublicacion, salida, dia(30), "DISPONIBLE");
        return idReserva;
    }

    private List<Map<String, Object>> periodosDeLaPublicacion() {
        return jdbcTemplate.queryForList("""
                SELECT fecha_inicio, fecha_fin, tipo, id_reserva
                FROM periodo_disponibilidad WHERE id_publicacion = ? ORDER BY fecha_inicio
                """, idPublicacion);
    }

    private static void assertPeriodo(Map<String, Object> periodo, LocalDate inicio, LocalDate fin, String tipo,
                                      Long idReserva) {
        assertThat(((Date) periodo.get("fecha_inicio")).toLocalDate()).isEqualTo(inicio);
        assertThat(((Date) periodo.get("fecha_fin")).toLocalDate()).isEqualTo(fin);
        assertThat(periodo.get("tipo")).isEqualTo(tipo);
        assertThat(periodo.get("id_reserva")).isEqualTo(idReserva);
    }

    private String estadoDe(long idReserva) {
        return jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class,
                idReserva);
    }

    private int contar(String sql) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class);
        return total == null ? 0 : total;
    }

    /**
     * Lanza todas las tareas a la vez, desde un mismo punto de partida, y devuelve sus resultados en el orden en
     * que se pidieron.
     */
    private static <T> List<T> ejecutarEnParalelo(List<Callable<T>> tareas) throws Exception {
        ExecutorService ejecutor = Executors.newFixedThreadPool(tareas.size());
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<T>> pendientes = new ArrayList<>();
            for (Callable<T> tarea : tareas) {
                pendientes.add(ejecutor.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            salida.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> pendiente : pendientes) {
                resultados.add(pendiente.get(60, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            ejecutor.shutdownNow();
        }
    }
}
