package com.inversioneslr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.BaseDeDatosDePrueba;
import com.jayway.jsonpath.JsonPath;
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

import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cotización de una estadía por un Huésped: desglose, servicios adicionales, disponibilidad y que no deje rastro.
 * PostgreSQL real con las migraciones, para comprobar el calendario y las restricciones de la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CotizacionIntegrationTest {

    private static final String URL = "/api/v1/cotizaciones";
    private static final String URL_RESERVAS = "/api/v1/reservas";
    /** Las fechas parten de seis meses adelante para no depender del día en que corre la prueba. */
    private static final LocalDate BASE = LocalDate.now().plusMonths(6);
    private static final String MENSAJE_SIN_COTIZACION =
            "No se puede generar una cotización para las fechas seleccionadas";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario huesped;
    private long idPublicacion;

    @Autowired
    CotizacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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

    // ------------------------------------------------------------------ desglose

    @Test
    @DisplayName("Cotiza noches, base, limpieza, tarifa de servicio y total, sin descuento ni servicios")
    void cotizar_conCalendarioDisponible_devuelveElDesglose() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(idPublicacion))
                .andExpect(jsonPath("$.fechaLlegada").value(dia(10).toString()))
                .andExpect(jsonPath("$.fechaSalida").value(dia(13).toString()))
                .andExpect(jsonPath("$.noches").value(3))
                .andExpect(jsonPath("$.precio.precioNoche").value(250000.00))
                .andExpect(jsonPath("$.precio.precioBase").value(750000.00))
                .andExpect(jsonPath("$.precio.porcentajeDescuento").value(0))
                .andExpect(jsonPath("$.precio.valorDescuento").value(0))
                .andExpect(jsonPath("$.precio.tarifaLimpieza").value(50000.00))
                .andExpect(jsonPath("$.precio.serviciosAdicionales", empty()))
                .andExpect(jsonPath("$.precio.totalServiciosAdicionales").value(0))
                .andExpect(jsonPath("$.precio.porcentajeTarifaServicio").value(10.00))
                .andExpect(jsonPath("$.precio.tarifaServicio").value(75000.00))
                .andExpect(jsonPath("$.precio.total").value(875000.00));
    }

    @Test
    @DisplayName("Una estadía de 7 noches o más recibe el descuento configurado")
    void cotizar_conEstadiaLarga_aplicaElDescuento() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(17), 2, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noches").value(7))
                .andExpect(jsonPath("$.precio.precioBase").value(1750000.00))
                .andExpect(jsonPath("$.precio.porcentajeDescuento").value(10.00))
                .andExpect(jsonPath("$.precio.valorDescuento").value(175000.00))
                .andExpect(jsonPath("$.precio.tarifaServicio").value(157500.00))
                .andExpect(jsonPath("$.precio.total").value(1782500.00));
    }

    @Test
    @DisplayName("Seis noches todavía no reciben descuento")
    void cotizar_conSeisNoches_noAplicaElDescuento() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(16), 2, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noches").value(6))
                .andExpect(jsonPath("$.precio.porcentajeDescuento").value(0))
                .andExpect(jsonPath("$.precio.total").value(1500000.00 + 50000.00 + 150000.00));
    }

    @Test
    @DisplayName("Los huéspedes son opcionales y no cambian el precio")
    void cotizar_conOSinHuespedes_dalMismoPrecio() throws Exception {
        for (Integer huespedes : new Integer[]{null, 1, 4}) {
            cotizar(huesped, cuerpo(dia(10), dia(13), huespedes, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.precio.total").value(875000.00));
        }
    }

    // ------------------------------------------------------------------ servicios adicionales

    @Test
    @DisplayName("Los servicios elegidos se suman al total, cada uno con su valor según el tipo de cobro")
    void cotizar_conServicios_losIncluyeConSuValor() throws Exception {
        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");
        servicio(idPublicacion, "Traslado al aeropuerto", "80000.00", "POR_ESTANCIA");
        servicio(idPublicacion, "Spa", "150000.00", "POR_ESTANCIA");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Desayuno", "Traslado al aeropuerto")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio.serviciosAdicionales.length()").value(2))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].nombre").value("Desayuno"))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].tipoCobro").value("POR_NOCHE"))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].precio").value(20000.00))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].valor").value(60000.00))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[1].nombre").value("Traslado al aeropuerto"))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[1].tipoCobro").value("POR_ESTANCIA"))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[1].valor").value(80000.00))
                .andExpect(jsonPath("$.precio.totalServiciosAdicionales").value(140000.00))
                // La tarifa de servicio sigue siendo la de la estadía: los servicios no la pagan.
                .andExpect(jsonPath("$.precio.tarifaServicio").value(75000.00))
                .andExpect(jsonPath("$.precio.total").value(1015000.00));
    }

    @Test
    @DisplayName("Los servicios salen en el orden en que se pidieron")
    void cotizar_conServicios_respetaElOrdenPedido() throws Exception {
        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");
        servicio(idPublicacion, "Traslado al aeropuerto", "80000.00", "POR_ESTANCIA");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Traslado al aeropuerto", "Desayuno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].nombre").value("Traslado al aeropuerto"))
                .andExpect(jsonPath("$.precio.serviciosAdicionales[1].nombre").value("Desayuno"));
    }

    @Test
    @DisplayName("En una estadía larga el descuento no alcanza a los servicios")
    void cotizar_conEstadiaLargaYServicio_elDescuentoNoLoAfecta() throws Exception {
        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");

        cotizar(huesped, cuerpo(dia(10), dia(17), 2, List.of("Desayuno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].valor").value(140000.00))
                .andExpect(jsonPath("$.precio.total").value(1782500.00 + 140000.00));
    }

    @Test
    @DisplayName("Un servicio gratuito aparece con valor cero")
    void cotizar_conServicioGratuito_apareceConValorCero() throws Exception {
        servicio(idPublicacion, "Estacionamiento", "0.00", "POR_NOCHE");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Estacionamiento")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio.serviciosAdicionales[0].valor").value(0))
                .andExpect(jsonPath("$.precio.total").value(875000.00));
    }

    @Test
    @DisplayName("Un servicio de otra publicación no se puede cotizar aquí")
    void cotizar_conServicioDeOtraPublicacion_responde400() throws Exception {
        long otra = insertarPublicacion("ACTIVA");
        servicio(otra, "Spa", "150000.00", "POR_ESTANCIA");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Spa")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.serviciosAdicionales", containsString("Spa")));
    }

    @Test
    @DisplayName("Un servicio que no existe responde 400 nombrándolo")
    void cotizar_conServicioInexistente_responde400() throws Exception {
        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Desayuno", "Jacuzzi")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.serviciosAdicionales", containsString("Jacuzzi")));
    }

    @Test
    @DisplayName("Un servicio repetido responde 400")
    void cotizar_conServicioRepetido_responde400() throws Exception {
        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Desayuno", "Desayuno")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.serviciosAdicionales", notNullValue()));
    }

    @Test
    @DisplayName("Un nombre de servicio en blanco responde 400")
    void cotizar_conNombreDeServicioEnBlanco_responde400() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['serviciosAdicionales[0]']", notNullValue()));
    }

    @Test
    @DisplayName("Más de 20 servicios responde 400")
    void cotizar_conDemasiadosServicios_responde400() throws Exception {
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            nombres.add("Servicio " + i);
        }

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, nombres))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.serviciosAdicionales", notNullValue()));
    }

    // ------------------------------------------------------------------ disponibilidad

    @Test
    @DisplayName("Una estadía que cruza dos tramos contiguos se cotiza")
    void cotizar_cruzandoDosTramosContiguos_seCotiza() throws Exception {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        periodo(idPublicacion, dia(0), dia(11), "DISPONIBLE");
        periodo(idPublicacion, dia(11), dia(30), "DISPONIBLE");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio.total").value(875000.00));
    }

    @Test
    @DisplayName("Sin calendario para las noches pedidas no se genera la cotización")
    void cotizar_sinCalendarioParaLasNoches_responde409() throws Exception {
        cotizar(huesped, cuerpo(dia(40), dia(42), 2, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_SIN_COTIZACION));
        cotizar(huesped, cuerpo(dia(28), dia(32), 2, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_SIN_COTIZACION));
    }

    @Test
    @DisplayName("Un hueco o una noche bloqueada en medio impiden cotizar")
    void cotizar_conHuecoONocheBloqueada_responde409() throws Exception {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        periodo(idPublicacion, dia(0), dia(11), "DISPONIBLE");
        periodo(idPublicacion, dia(11), dia(12), "BLOQUEADO");
        periodo(idPublicacion, dia(12), dia(30), "DISPONIBLE");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null)).andExpect(status().isConflict());

        jdbcTemplate.update("DELETE FROM periodo_disponibilidad WHERE tipo = 'BLOQUEADO'");
        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Noches ya reservadas por otro Huésped no se pueden cotizar, pero las vecinas sí")
    void cotizar_conNochesReservadas_responde409() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        insertarReservaPendienteConTramo(otro, dia(10), dia(13), "clave-otro",
                OffsetDateTime.now().plusMinutes(30));

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null)).andExpect(status().isConflict());
        cotizar(huesped, cuerpo(dia(12), dia(15), 2, null)).andExpect(status().isConflict());
        // La salida del otro Huésped es la llegada de este: no se pisan.
        cotizar(huesped, cuerpo(dia(13), dia(15), 2, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noches").value(2));
        cotizar(huesped, cuerpo(dia(8), dia(10), 2, null)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una reserva pendiente vencida que aún no se libera sigue ocupando sus noches al cotizar")
    void cotizar_conReservaPendienteVencidaSinLiberar_responde409() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);
        insertarReservaPendienteConTramo(otro, dia(10), dia(13), "clave-vencida",
                OffsetDateTime.now().minusMinutes(5));

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null)).andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ no deja rastro

    @Test
    @DisplayName("Cotizar no crea reservas, no toca el calendario ni deja auditoría")
    void cotizar_noDejaRastro() throws Exception {
        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Desayuno"))).andExpect(status().isOk());
        cotizar(huesped, cuerpo(dia(10), dia(13), 2, List.of("Desayuno"))).andExpect(status().isOk());

        assertThat(contar("SELECT COUNT(*) FROM reserva")).isZero();
        assertThat(contar("SELECT COUNT(*) FROM evento_auditoria")).isZero();
        List<Map<String, Object>> periodos = jdbcTemplate.queryForList(
                "SELECT fecha_inicio, fecha_fin, tipo FROM periodo_disponibilidad");
        assertThat(periodos).hasSize(1);
        assertThat(periodos.get(0)).containsEntry("tipo", "DISPONIBLE");
    }

    @Test
    @DisplayName("Cotizar no ocupa las fechas: otro Huésped puede reservarlas después")
    void cotizar_noOcupaLasFechas() throws Exception {
        Usuario otro = crearUsuario("otro@correo.com", Rol.HUESPED);

        cotizar(huesped, cuerpo(dia(10), dia(13), 2, null)).andExpect(status().isOk());

        reservar(otro, "clave-otro", dia(10), dia(13)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("La reserva cobra lo mismo que la cotización")
    void cotizar_yReservar_danElMismoTotal() throws Exception {
        for (int[] estadia : new int[][]{{10, 13}, {10, 17}}) {
            String total = cotizar(huesped, cuerpo(dia(estadia[0]), dia(estadia[1]), 2, null))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Number totalCotizado = JsonPath.read(total, "$.precio.total");

            reservar(huesped, "clave-" + estadia[1], dia(estadia[0]), dia(estadia[1]))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.precio.total").value(totalCotizado));
        }
    }

    // ------------------------------------------------------------------ validaciones

    @Test
    @DisplayName("Sin token responde 401")
    void cotizar_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(dia(10), dia(13), 2, null)))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"ANFITRION", "ADMINISTRADOR"})
    @DisplayName("Solo el rol Huésped cotiza")
    void cotizar_conOtroRol_responde403(String rol) throws Exception {
        Usuario otroRol = crearUsuario("rol@correo.com", Rol.valueOf(rol));

        cotizar(otroRol, cuerpo(dia(10), dia(13), 2, null)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Llegar antes de hoy responde 400 en el campo de la llegada")
    void cotizar_conLlegadaPasada_responde400() throws Exception {
        cotizar(huesped, cuerpo(LocalDate.now().minusDays(1), dia(13), 2, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaLlegada", containsString("anterior a hoy")));
    }

    @Test
    @DisplayName("Más huéspedes que la capacidad responde 400 en el campo de huéspedes")
    void cotizar_conMasHuespedesQueLaCapacidad_responde400() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(13), 5, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.huespedes", containsString("4")));
    }

    @Test
    @DisplayName("Cero huéspedes responde 400")
    void cotizar_conCeroHuespedes_responde400() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(13), 0, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.huespedes", notNullValue()));
    }

    @Test
    @DisplayName("Una salida igual o anterior a la llegada responde 400")
    void cotizar_conSalidaNoPosterior_responde400() throws Exception {
        cotizar(huesped, cuerpo(dia(10), dia(10), 2, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.rangoFechasValido", notNullValue()));
        cotizar(huesped, cuerpo(dia(10), dia(9), 2, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.rangoFechasValido", notNullValue()));
    }

    @Test
    @DisplayName("Los campos obligatorios ausentes responden 400 con cada error")
    void cotizar_conDatosIncompletos_responde400() throws Exception {
        cotizar(huesped, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idPublicacion", notNullValue()))
                .andExpect(jsonPath("$.errores.fechaLlegada", notNullValue()))
                .andExpect(jsonPath("$.errores.fechaSalida", notNullValue()));
    }

    @Test
    @DisplayName("Una fecha con formato inválido responde 400 en su campo")
    void cotizar_conFechaMalFormada_responde400() throws Exception {
        cotizar(huesped, """
                {"idPublicacion": %d, "fechaLlegada": "mañana", "fechaSalida": "%s"}
                """.formatted(idPublicacion, dia(13)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.fechaLlegada", notNullValue()));
    }

    @Test
    @DisplayName("Un cuerpo que no es JSON responde 400")
    void cotizar_conCuerpoIlegible_responde400() throws Exception {
        cotizar(huesped, "esto no es json").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Una publicación inexistente responde 404")
    void cotizar_conPublicacionInexistente_responde404() throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idPublicacion", 999999);
        cuerpo.put("fechaLlegada", dia(10).toString());
        cuerpo.put("fechaSalida", dia(13).toString());

        cotizar(huesped, objectMapper.writeValueAsString(cuerpo))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("El alojamiento no está disponible"));
    }

    @ParameterizedTest(name = "publicación {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Una publicación que no está ACTIVA no se cotiza")
    void cotizar_conPublicacionNoActiva_responde404(String estado) throws Exception {
        long noActiva = insertarPublicacion(estado);
        periodo(noActiva, dia(0), dia(30), "DISPONIBLE");

        cotizar(huesped, cuerpo(noActiva, dia(10), dia(13), 2, null)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ base de datos

    @Test
    @DisplayName("La base solo admite los tipos de cobro POR_NOCHE y POR_ESTANCIA")
    void servicioAdicional_conTipoDeCobroDesconocido_laBaseLoRechaza() {
        assertThatThrownBy(() -> servicio(idPublicacion, "Spa", "150000.00", "POR_HORA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_servicio_adicional_tipo_cobro");

        servicio(idPublicacion, "Desayuno", "20000.00", "POR_NOCHE");
        servicio(idPublicacion, "Traslado", "80000.00", "POR_ESTANCIA");
        assertThat(contar("SELECT COUNT(*) FROM servicio_adicional")).isEqualTo(2);
    }

    // ------------------------------------------------------------------ apoyo

    private static LocalDate dia(int desdeLaBase) {
        return BASE.plusDays(desdeLaBase);
    }

    private ResultActions cotizar(Usuario quien, String cuerpoJson) throws Exception {
        return mockMvc.perform(post(URL)
                .header(HttpHeaders.AUTHORIZATION, token(quien))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpoJson));
    }

    private ResultActions reservar(Usuario quien, String clave, LocalDate llegada, LocalDate salida)
            throws Exception {
        return mockMvc.perform(post(URL_RESERVAS)
                .header(HttpHeaders.AUTHORIZATION, token(quien))
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo(llegada, salida, 2, null)));
    }

    private String cuerpo(LocalDate llegada, LocalDate salida, Integer huespedes, List<String> servicios)
            throws Exception {
        return cuerpo(idPublicacion, llegada, salida, huespedes, servicios);
    }

    private String cuerpo(long idDePublicacion, LocalDate llegada, LocalDate salida, Integer huespedes,
                          List<String> servicios) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("idPublicacion", idDePublicacion);
        cuerpo.put("fechaLlegada", llegada.toString());
        cuerpo.put("fechaSalida", salida.toString());
        if (huespedes != null) {
            cuerpo.put("huespedes", huespedes);
        }
        if (servicios != null) {
            cuerpo.put("serviciosAdicionales", servicios);
        }
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

    private void servicio(long idDePublicacion, String nombre, String precio, String tipoCobro) {
        jdbcTemplate.update("""
                INSERT INTO servicio_adicional (id_publicacion, nombre, precio, tipo_cobro)
                VALUES (?, ?, ?::numeric, ?)
                """, idDePublicacion, nombre, precio, tipoCobro);
    }

    /**
     * Una reserva pendiente con su tramo reservado, como la dejaría el flujo normal: el tramo disponible grande
     * queda partido alrededor de las noches reservadas.
     */
    private void insertarReservaPendienteConTramo(Usuario quien, LocalDate llegada, LocalDate salida, String clave,
                                                  OffsetDateTime vence) {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        Long idReserva = jdbcTemplate.queryForObject("""
                INSERT INTO reserva (id_huesped, id_publicacion, fecha_llegada, fecha_salida, estado,
                                     clave_idempotencia, precio_base, porcentaje_descuento_aplicado, valor_descuento,
                                     tarifa_limpieza, porcentaje_tarifa_servicio, tarifa_servicio, total,
                                     horas_anticipacion_aplicada, porcentaje_devolucion_aplicado, fecha_expiracion)
                VALUES (?, ?, ?, ?, 'PENDIENTE_PAGO', ?, 100000, 0, 0, 0, 0, 0, 100000, 24, 100, ?)
                RETURNING id_reserva
                """, Long.class, quien.getIdUsuario(), idPublicacion, Date.valueOf(llegada), Date.valueOf(salida),
                clave, vence);
        periodo(idPublicacion, dia(0), llegada, "DISPONIBLE");
        jdbcTemplate.update("""
                INSERT INTO periodo_disponibilidad (id_publicacion, id_reserva, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, ?, 'RESERVADO')
                """, idPublicacion, idReserva, Date.valueOf(llegada), Date.valueOf(salida));
        periodo(idPublicacion, salida, dia(30), "DISPONIBLE");
    }

    private int contar(String sql) {
        Integer total = jdbcTemplate.queryForObject(sql, Integer.class);
        return total == null ? 0 : total;
    }
}
