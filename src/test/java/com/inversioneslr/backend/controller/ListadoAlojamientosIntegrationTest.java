package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.BaseDeDatosDePrueba;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.TokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta pública de alojamientos disponibles: solo las publicaciones ACTIVAS, sin necesidad de token.
 * PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ListadoAlojamientosIntegrationTest {

    private static final String URL = "/api/v1/alojamientos";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;

    @Autowired
    ListadoAlojamientosIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate,
                                       UsuarioRepository usuarioRepository, TokenService tokenService) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    @BeforeEach
    void preparar() {
        BaseDeDatosDePrueba.limpiar(jdbcTemplate);
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
    }

    @Test
    @DisplayName("Un Visitante sin token ve las publicaciones ACTIVAS")
    void listar_sinToken_muestraLasActivas() throws Exception {
        long id = insertarPublicacion("Casa en el centro", "ACTIVA");

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Casa en el centro"))
                .andExpect(jsonPath("$.contenido[0].estado").value("ACTIVA"))
                .andExpect(jsonPath("$.contenido[0].ciudad.nombre").value("Cartagena"))
                .andExpect(jsonPath("$.contenido[0].tipo.nombre").value("Apartamento"))
                .andExpect(jsonPath("$.contenido[0].capacidad").value(4))
                .andExpect(jsonPath("$.contenido[0].precioNoche").value(250000.00))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    @DisplayName("Criterio 2: solo aparece la ACTIVA entre publicaciones en todos los estados")
    void listar_conTodosLosEstados_soloMuestraLaActiva() throws Exception {
        insertarPublicacion("Borrador", "BORRADOR");
        insertarPublicacion("En revisión", "PENDIENTE_REVISION");
        long activa = insertarPublicacion("Activa", "ACTIVA");
        insertarPublicacion("Pausada", "PAUSADA");
        insertarPublicacion("Bloqueada", "BLOQUEADA");

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(activa))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Criterio 2: una publicación fuera de ACTIVA no se presenta como disponible")
    void listar_estadoDistintoDeActiva_noAparece(String estado) throws Exception {
        insertarPublicacion("Casa no disponible", estado);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.mensaje").value("No hay alojamientos disponibles por el momento"));
    }

    @Test
    @DisplayName("Una publicación pausada vuelve a mostrarse si regresa a ACTIVA")
    void listar_alVolverAActiva_vuelveAAparecer() throws Exception {
        long id = insertarPublicacion("Casa en el centro", "PAUSADA");
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.totalElementos").value(0));

        jdbcTemplate.update("UPDATE publicacion SET estado = 'ACTIVA' WHERE id_publicacion = ?", id);

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    @DisplayName("El listado público no expone dirección, reglas ni datos del Anfitrión")
    void listar_noExponeDatosPrivados() throws Exception {
        insertarPublicacion("Casa en el centro", "ACTIVA");

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].direccionTextual").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].reglas").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].descripcion").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].idAnfitrion").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].tarifaLimpieza").doesNotExist());
    }

    @Test
    @DisplayName("Los alojamientos salen del más reciente al más antiguo")
    void listar_ordenaDelMasRecienteAlMasAntiguo() throws Exception {
        insertarPublicacion("Primera", "ACTIVA");
        insertarPublicacion("Segunda", "ACTIVA");
        insertarPublicacion("Tercera", "ACTIVA");

        mockMvc.perform(get(URL))
                .andExpect(jsonPath("$.contenido[*].titulo", contains("Tercera", "Segunda", "Primera")));
    }

    @Test
    void listar_pagina_devuelveElTrozoPedidoConLosTotales() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertarPublicacion("Casa " + i, "ACTIVA");
        }

        mockMvc.perform(get(URL + "?pagina=1&tamano=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].titulo", contains("Casa 3", "Casa 2")))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void listar_sinParametros_usaPaginaCeroYTamanoVeinte() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20));
    }

    @Test
    void listar_sinAlojamientos_informaQueNoHay() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.mensaje").value("No hay alojamientos disponibles por el momento"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "?tamano=0, tamano",
            "?tamano=101, tamano",
            "?pagina=-1, pagina",
            "?pagina=abc, pagina"
    })
    void listar_parametrosInvalidos_responde400EnElCampo(String consulta, String campo) throws Exception {
        mockMvc.perform(get(URL + consulta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION", "ADMINISTRADOR"})
    @DisplayName("Un usuario autenticado, de cualquier rol, ve el mismo listado")
    void listar_conToken_muestraElMismoListado(String rol) throws Exception {
        insertarPublicacion("Casa en el centro", "ACTIVA");
        Usuario usuario = crearUsuario("consulta-" + rol.toLowerCase() + "@correo.com", Rol.valueOf(rol));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)));
    }

    @Test
    @DisplayName("Un token inválido no impide la consulta pública")
    void listar_conTokenInvalido_respondeIgual() throws Exception {
        insertarPublicacion("Casa en el centro", "ACTIVA");

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, "Bearer token-que-no-es-valido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)));
    }

    @Test
    @DisplayName("Solo la consulta es pública: crear sobre la misma ruta exige token")
    void escribir_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void contratoOpenApi_documentaLaConsultaPublica() throws Exception {
        String ruta = "$.paths['" + URL + "'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ruta + ".responses['200']", notNullValue()))
                .andExpect(jsonPath(ruta + ".responses['400']", notNullValue()))
                .andExpect(jsonPath(ruta + ".security").doesNotExist());
    }

    @ParameterizedTest(name = "ACTIVA -> {0}")
    @ValueSource(strings = {"PAUSADA", "BLOQUEADA"})
    @DisplayName("HU-022: una publicación deja de aparecer al dejar de estar ACTIVA")
    void listar_activaCambiaAEstadoNoDisponible_dejaDeAparecer(String estado) throws Exception {
        long id = insertarPublicacion("Casa inicialmente activa", "ACTIVA");

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id))
                .andExpect(jsonPath("$.totalElementos").value(1));

        jdbcTemplate.update(
                "UPDATE publicacion SET estado = ? WHERE id_publicacion = ?",
                estado, id);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.mensaje")
                        .value("No hay alojamientos disponibles por el momento"));
    }

    @Test
    @DisplayName("HU-022: una página fuera de rango conserva los totales sin mensaje de ausencia de oferta")
    void listar_paginaFueraDeRango_conservaTotalesSinMensajeVacio() throws Exception {
        insertarPublicacion("Casa activa", "ACTIVA");

        mockMvc.perform(get(URL)
                        .param("pagina", "3")
                        .param("tamano", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.pagina").value(3))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    void filtrar_ciudad_soloDevuelveActivasDeLaCiudad() throws Exception {
        long cartagena = insertarPublicacion("Cartagena activa", "ACTIVA");
        long bogota = insertarPublicacion("Bogotá activa", "ACTIVA");
        insertarPublicacion("Cartagena pausada", "PAUSADA");

        jdbcTemplate.update(
                "UPDATE publicacion SET id_ciudad = ? WHERE id_publicacion = ?",
                ciudad("Bogotá"), bogota);

        mockMvc.perform(get(URL).param("idCiudad", String.valueOf(ciudad("Cartagena"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(cartagena));
    }

    @Test
    void filtrar_huespedes_incluyeCapacidadIgualYMayor() throws Exception {
        long pequena = insertarPublicacion("Para dos", "ACTIVA");
        long exacta = insertarPublicacion("Para cuatro", "ACTIVA");
        long grande = insertarPublicacion("Para seis", "ACTIVA");

        jdbcTemplate.update(
                "UPDATE publicacion SET capacidad = 2 WHERE id_publicacion = ?", pequena);
        jdbcTemplate.update(
                "UPDATE publicacion SET capacidad = 6 WHERE id_publicacion = ?", grande);

        mockMvc.perform(get(URL).param("huespedes", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].idPublicacion",
                        contains((int) grande, (int) exacta)))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void filtrar_fechas_exigeDisponibilidadRegistrada() throws Exception {
        insertarPublicacion("Sin calendario", "ACTIVA");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.mensaje").value(
                        "No hay alojamientos que coincidan con los filtros seleccionados"));
    }

    @Test
    void filtrar_fechas_aceptaCoberturaExacta() throws Exception {
        long id = insertarPublicacion("Disponible", "ACTIVA");
        periodo(id, "2030-06-10", "2030-06-13", "DISPONIBLE");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    void filtrar_fechas_aceptaPeriodoMasAmplio() throws Exception {
        long id = insertarPublicacion("Disponible", "ACTIVA");
        periodo(id, "2030-06-01", "2030-06-30", "DISPONIBLE");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    void filtrar_fechas_unePeriodosDisponiblesContiguos() throws Exception {
        long id = insertarPublicacion("Periodos contiguos", "ACTIVA");
        periodo(id, "2030-06-10", "2030-06-11", "DISPONIBLE");
        periodo(id, "2030-06-11", "2030-06-13", "DISPONIBLE");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    void filtrar_fechas_noAceptaHuecosEntrePeriodos() throws Exception {
        long id = insertarPublicacion("Con hueco", "ACTIVA");
        periodo(id, "2030-06-10", "2030-06-11", "DISPONIBLE");
        periodo(id, "2030-06-12", "2030-06-13", "DISPONIBLE");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void filtrar_fechas_noAceptaCoberturaParcial() throws Exception {
        long id = insertarPublicacion("Cobertura parcial", "ACTIVA");
        periodo(id, "2030-06-10", "2030-06-12", "DISPONIBLE");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void filtrar_fechas_excluyeUnaNocheBloqueada() throws Exception {
        long id = insertarPublicacion("Con bloqueo", "ACTIVA");
        periodo(id, "2030-06-10", "2030-06-11", "DISPONIBLE");
        periodo(id, "2030-06-11", "2030-06-12", "BLOQUEADO");
        periodo(id, "2030-06-12", "2030-06-13", "DISPONIBLE");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMADA", "REVISION_MANUAL"})
    void filtrar_fechas_excluyeReservaQueOcupaElRango(String estado) throws Exception {
        long id = insertarPublicacion("Con reserva", "ACTIVA");
        periodo(id, "2030-06-01", "2030-06-30", "DISPONIBLE");
        reserva(id, "2030-06-11", "2030-06-12", estado);

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void filtrar_fechas_reservaCanceladaNoOcupaElRango() throws Exception {
        long id = insertarPublicacion("Reserva cancelada", "ACTIVA");
        periodo(id, "2030-06-01", "2030-06-30", "DISPONIBLE");
        reserva(id, "2030-06-11", "2030-06-12", "CANCELADA");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @ParameterizedTest
    @CsvSource({
            "2030-06-08, 2030-06-10",
            "2030-06-13, 2030-06-15"
    })
    void filtrar_fechas_reservasEnLosBordesNoSeCruzan(
            String inicio, String fin) throws Exception {
        long id = insertarPublicacion("Reserva contigua", "ACTIVA");
        periodo(id, "2030-06-01", "2030-06-30", "DISPONIBLE");
        reserva(id, inicio, fin, "CONFIRMADA");

        buscarConFechas()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    void filtrar_combinaCiudadFechasCapacidadYPaginacion() throws Exception {
        long primera = insertarPublicacion("Primera válida", "ACTIVA");
        long segunda = insertarPublicacion("Segunda válida", "ACTIVA");
        long otraCiudad = insertarPublicacion("Otra ciudad", "ACTIVA");
        long pocaCapacidad = insertarPublicacion("Poca capacidad", "ACTIVA");

        for (long id : new long[]{primera, segunda, otraCiudad, pocaCapacidad}) {
            periodo(id, "2030-06-01", "2030-06-30", "DISPONIBLE");
        }

        jdbcTemplate.update(
                "UPDATE publicacion SET id_ciudad = ? WHERE id_publicacion = ?",
                ciudad("Bogotá"), otraCiudad);
        jdbcTemplate.update(
                "UPDATE publicacion SET capacidad = 2 WHERE id_publicacion = ?",
                pocaCapacidad);

        mockMvc.perform(get(URL)
                        .param("idCiudad", String.valueOf(ciudad("Cartagena")))
                        .param("fechaLlegada", "2030-06-10")
                        .param("fechaSalida", "2030-06-13")
                        .param("huespedes", "4")
                        .param("pagina", "1")
                        .param("tamano", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(primera))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @ParameterizedTest
    @CsvSource({
            "?idCiudad=0, idCiudad",
            "?idCiudad=-1, idCiudad",
            "?idCiudad=abc, idCiudad",
            "?huespedes=0, huespedes",
            "?huespedes=-1, huespedes",
            "?huespedes=2.5, huespedes",
            "?fechaLlegada=no-es-fecha, fechaLlegada",
            "?fechaLlegada=2030-06-10, fechasCompletas",
            "?fechaSalida=2030-06-13, fechasCompletas",
            "?fechaLlegada=2030-06-10&fechaSalida=2030-06-10, rangoFechasValido",
            "?fechaLlegada=2030-06-13&fechaSalida=2030-06-10, rangoFechasValido"
    })
    void filtrar_datosInvalidos_responde400PorCampo(
            String consulta, String campo) throws Exception {
        mockMvc.perform(get(URL + consulta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    @Test
    void ciudadesBusqueda_sinToken_devuelveElCatalogo() throws Exception {
        mockMvc.perform(get(URL + "/ciudades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre",
                        contains("Bogotá", "Cali", "Cartagena", "Medellín")));
    }

    @Test
    void catalogoPrivadoDeCiudades_sigueExigiendoSesion() throws Exception {
        mockMvc.perform(get("/api/v1/ciudades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hu024_tipo_soloDevuelveActivasDelTipoSeleccionado() throws Exception {
        long apartamento = insertarPublicacion("Apartamento activo", "ACTIVA");
        long casa = insertarPublicacion("Casa activa", "ACTIVA");
        insertarPublicacion("Apartamento pausado", "PAUSADA");

        jdbcTemplate.update(
                "UPDATE publicacion SET id_tipo = ? WHERE id_publicacion = ?",
                tipo("Casa"), casa);

        mockMvc.perform(get(URL)
                        .param("idTipo", String.valueOf(tipo("Apartamento"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(apartamento))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @ParameterizedTest
    @CsvSource({
            "precioMinimo, 250000",
            "precioMaximo, 250000"
    })
    void hu024_precio_limitesSonInclusivos(
            String parametro, String valor) throws Exception {
        long id = insertarPublicacion("En el límite", "ACTIVA");

        mockMvc.perform(get(URL).param(parametro, valor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    void hu024_precio_filtraAntesDePaginarYConservaTotales() throws Exception {
        long primera = insertarPublicacion("Primera válida", "ACTIVA");
        insertarPublicacion("Segunda válida", "ACTIVA");
        long fuera = insertarPublicacion("Fuera del precio", "ACTIVA");

        jdbcTemplate.update(
                "UPDATE publicacion SET precio_noche = 500000 WHERE id_publicacion = ?",
                fuera);

        mockMvc.perform(get(URL)
                        .param("precioMinimo", "200000")
                        .param("precioMaximo", "300000")
                        .param("pagina", "1")
                        .param("tamano", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(primera))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @Test
    void hu024_servicios_exigeTodosSinDuplicarPublicaciones() throws Exception {
        long wifi = servicioCatalogo("Wi-Fi");
        long cocina = servicioCatalogo("Cocina");

        long completa = insertarPublicacion("Con ambos servicios", "ACTIVA");
        long parcial = insertarPublicacion("Solo wifi", "ACTIVA");
        insertarPublicacion("Sin servicios", "ACTIVA");
        long pausada = insertarPublicacion("Pausada con ambos", "PAUSADA");

        asociarServicio(completa, wifi);
        asociarServicio(completa, cocina);
        asociarServicio(parcial, wifi);
        asociarServicio(pausada, wifi);
        asociarServicio(pausada, cocina);

        mockMvc.perform(get(URL)
                        .param("idsServicios",
                                String.valueOf(wifi), String.valueOf(cocina)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(completa))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void hu024_servicioRepetido_noCambiaElResultado() throws Exception {
        long wifi = servicioCatalogo("Wi-Fi");
        long id = insertarPublicacion("Con wifi", "ACTIVA");
        asociarServicio(id, wifi);

        mockMvc.perform(get(URL)
                        .param("idsServicios",
                                String.valueOf(wifi), String.valueOf(wifi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void hu024_combinaCaracteristicasConCiudadFechasYCapacidad() throws Exception {
        long wifi = servicioCatalogo("Wi-Fi");
        long valida = insertarPublicacion("Cumple todo", "ACTIVA");
        long sinServicio = insertarPublicacion("Sin servicio", "ACTIVA");
        long sinFechas = insertarPublicacion("Sin disponibilidad", "ACTIVA");
        long cara = insertarPublicacion("Demasiado cara", "ACTIVA");
        long otroTipo = insertarPublicacion("Otro tipo", "ACTIVA");

        for (long id : new long[]{valida, sinServicio, cara, otroTipo}) {
            periodo(id, "2030-06-10", "2030-06-13", "DISPONIBLE");
        }

        for (long id : new long[]{valida, sinFechas, cara, otroTipo}) {
            asociarServicio(id, wifi);
        }

        jdbcTemplate.update(
                "UPDATE publicacion SET precio_noche = 500000 WHERE id_publicacion = ?",
                cara);
        jdbcTemplate.update(
                "UPDATE publicacion SET id_tipo = ? WHERE id_publicacion = ?",
                tipo("Casa"), otroTipo);

        mockMvc.perform(get(URL)
                        .param("idCiudad", String.valueOf(ciudad("Cartagena")))
                        .param("fechaLlegada", "2030-06-10")
                        .param("fechaSalida", "2030-06-13")
                        .param("huespedes", "4")
                        .param("idTipo", String.valueOf(tipo("Apartamento")))
                        .param("precioMinimo", "200000")
                        .param("precioMaximo", "300000")
                        .param("idsServicios", String.valueOf(wifi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(valida))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void hu024_sinCoincidencias_informaElResultadoVacio() throws Exception {
        insertarPublicacion("Precio superior", "ACTIVA");

        mockMvc.perform(get(URL).param("precioMaximo", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.mensaje").value(
                        "No hay alojamientos que coincidan con los filtros seleccionados"));
    }

    @ParameterizedTest
    @CsvSource({
            "?idTipo=0, idTipo",
            "?idTipo=-1, idTipo",
            "?idTipo=abc, idTipo",
            "?precioMinimo=-1, precioMinimo",
            "?precioMaximo=-1, precioMaximo",
            "?precioMinimo=abc, precioMinimo",
            "?precioMaximo=100.001, precioMaximo",
            "?precioMinimo=300000&precioMaximo=100000, rangoPreciosValido"
    })
    void hu024_parametrosInvalidos_responde400PorCampo(
            String consulta, String campo) throws Exception {
        mockMvc.perform(get(URL + consulta))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void hu024_serviciosInvalidos_responde400(String valor) throws Exception {
        mockMvc.perform(get(URL).param("idsServicios", valor))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tiposBusqueda_sinToken_devuelveCatalogoOrdenado() throws Exception {
        mockMvc.perform(get("/api/v1/catalogos/tipos-alojamiento"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre",
                        contains("Apartamento", "Cabaña", "Casa", "Habitación")))
                .andExpect(jsonPath("$[0].id", notNullValue()));
    }

    @Test
    void serviciosBusqueda_sinToken_devuelveCatalogoOrdenadoConIdentificadores()
            throws Exception {

        long wifi = servicioCatalogo("Wi-Fi");

        List<String> nombresEsperados = jdbcTemplate.queryForList(
                "SELECT nombre FROM servicio ORDER BY nombre",
                String.class);

        mockMvc.perform(get("/api/v1/catalogos/servicios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(nombresEsperados.size())))
                .andExpect(jsonPath("$[*].nombre",
                        contains(nombresEsperados.toArray(new String[0]))))
                .andExpect(jsonPath("$[?(@.nombre == 'Wi-Fi')].id",
                        contains((int) wifi)));
    }

    @Test
    void catalogoPrivadoDeTipos_sigueExigiendoSesion() throws Exception {
        mockMvc.perform(get("/api/v1/tipos-alojamiento"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/catalogos/tipos-alojamiento",
            "/api/v1/catalogos/servicios"
    })
    void escribirCatalogosPublicos_sinToken_sigueExigiendoSesion(String ruta)
            throws Exception {
        mockMvc.perform(post(ruta))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/catalogos/tipos-alojamiento",
            "/api/v1/catalogos/servicios"
    })
    void contratoOpenApi_documentaNuevosCatalogosPublicos(String ruta)
            throws Exception {

        String operacion = "$.paths['" + ruta + "'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        operacion + ".responses['200']", notNullValue()))
                .andExpect(jsonPath(operacion + ".security").isArray())
                .andExpect(jsonPath(operacion + ".security").isEmpty());
    }

    @Test
    void contratoOpenApi_conservaSeguridadDelCatalogoPrivado() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/tipos-alojamiento'].get.security",
                        notNullValue()));
    }

    @Test
    void hu025_listado_incluyeSoloServiciosDeCadaPublicacion() throws Exception {
        long conServicios = insertarPublicacion("Con servicios", "ACTIVA");
        long sinServicios = insertarPublicacion("Sin servicios", "ACTIVA");
        long pausada = insertarPublicacion("Pausada", "PAUSADA");

        long cocina = servicioCatalogo("Cocina");
        long wifi = servicioCatalogo("Wi-Fi");
        long parqueadero = servicioCatalogo("Parqueadero");

        asociarServicio(conServicios, wifi);
        asociarServicio(conServicios, cocina);
        asociarServicio(pausada, parqueadero);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion")
                        .value(sinServicios))
                .andExpect(jsonPath("$.contenido[0].servicios", hasSize(0)))
                .andExpect(jsonPath("$.contenido[1].idPublicacion")
                        .value(conServicios))
                .andExpect(jsonPath("$.contenido[1].servicios", hasSize(2)))
                .andExpect(jsonPath("$.contenido[1].servicios[0].id")
                        .value(cocina))
                .andExpect(jsonPath("$.contenido[1].servicios[0].nombre")
                        .value("Cocina"))
                .andExpect(jsonPath("$.contenido[1].servicios[1].id")
                        .value(wifi))
                .andExpect(jsonPath("$.contenido[1].servicios[1].nombre")
                        .value("Wi-Fi"))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void hu025_listadoFiltrado_conservaServiciosYPaginacion() throws Exception {
        long primera = insertarPublicacion("Primera", "ACTIVA");
        long segunda = insertarPublicacion("Segunda", "ACTIVA");
        long wifi = servicioCatalogo("Wi-Fi");

        asociarServicio(primera, wifi);
        asociarServicio(segunda, wifi);

        mockMvc.perform(get(URL)
                        .param("idsServicios", String.valueOf(wifi))
                        .param("pagina", "1")
                        .param("tamano", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(primera))
                .andExpect(jsonPath("$.contenido[0].servicios", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].servicios[0].id").value(wifi))
                .andExpect(jsonPath("$.contenido[0].servicios[0].nombre").value("Wi-Fi"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    /**
     * Prepara publicaciones en distintos estados para probar la consulta pública de forma aislada
     * de los flujos de creación y moderación. Se completan descripción, dirección y reglas porque
     * la base de datos las exige para PENDIENTE_REVISION y ACTIVA.
     */
    private long insertarPublicacion(String titulo, String estado) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO publicacion (id_anfitrion, id_ciudad, id_tipo, id_politica, titulo, descripcion,
                                         direccion_textual, capacidad, precio_noche, tarifa_limpieza, reglas, estado)
                VALUES (?, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                        (SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'),
                        (SELECT id_politica FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        ?, 'Descripción de prueba', 'Calle 5 # 3-20', 4, 250000.00, 0, 'Reglas de prueba', ?)
                RETURNING id_publicacion
                """, Long.class, anfitrion.getIdUsuario(), titulo, estado);
        return id;
    }

    private String token(Usuario usuario) {
        return "Bearer " + tokenService.generar(usuario).token();
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.save(new Usuario(new NombreCompleto("Nombre", null, "Apellido", null),
                correo, "hash-no-usado", rol));
    }

    private long ciudad(String nombre) {
        return jdbcTemplate.queryForObject(
                "SELECT id_ciudad FROM ciudad WHERE nombre = ?",
                Long.class, nombre);
    }

    private void periodo(long idPublicacion, String inicio, String fin, String tipo) {
        jdbcTemplate.update("""
                INSERT INTO periodo_disponibilidad
                    (id_publicacion, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, ?)
                """,
                idPublicacion,
                Date.valueOf(LocalDate.parse(inicio)),
                Date.valueOf(LocalDate.parse(fin)),
                tipo);
    }

    private ResultActions buscarConFechas() throws Exception {
        return mockMvc.perform(get(URL)
                .param("fechaLlegada", "2030-06-10")
                .param("fechaSalida", "2030-06-13"));
    }

    private void reserva(long idPublicacion, String inicio, String fin, String estado) {
        Usuario huesped = crearUsuario("huesped-reserva@correo.com", Rol.HUESPED);

        jdbcTemplate.update("""
                INSERT INTO reserva (
                    id_huesped, id_publicacion, fecha_llegada, fecha_salida,
                    estado, clave_idempotencia, precio_base,
                    porcentaje_descuento_aplicado, valor_descuento,
                    tarifa_limpieza, porcentaje_tarifa_servicio, tarifa_servicio,
                    total, horas_anticipacion_aplicada, porcentaje_devolucion_aplicado
                )
                VALUES (?, ?, ?, ?, ?, ?, 100000, 0, 0, 0, 0, 0, 100000, 24, 100)
                """,
                huesped.getIdUsuario(), idPublicacion,
                Date.valueOf(LocalDate.parse(inicio)),
                Date.valueOf(LocalDate.parse(fin)),
                estado, "prueba-hu023-" + idPublicacion);
    }

    private long tipo(String nombre) {
        return jdbcTemplate.queryForObject(
                "SELECT id_tipo FROM tipo_alojamiento WHERE nombre = ?",
                Long.class, nombre);
    }

    private long servicioCatalogo(String nombre) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO servicio (nombre)
                VALUES (?)
                ON CONFLICT (nombre)
                DO UPDATE SET nombre = EXCLUDED.nombre
                RETURNING id_servicio
                """, Long.class, nombre);
    }

    private void asociarServicio(long idPublicacion, long idServicio) {
        jdbcTemplate.update("""
                INSERT INTO publicacion_servicio (id_publicacion, id_servicio)
                VALUES (?, ?)
                """, idPublicacion, idServicio);
    }

    @AfterEach
    void limpiarDisponibilidadDePrueba() {
        jdbcTemplate.update("DELETE FROM periodo_disponibilidad");
        jdbcTemplate.update("DELETE FROM reserva");
    }
}
