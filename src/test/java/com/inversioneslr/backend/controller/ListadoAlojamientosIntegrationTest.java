package com.inversioneslr.backend.controller;

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

    /**
     * Inserta directamente en la base: así se prueban estados a los que todavía ningún endpoint lleva. Las reglas
     * se llenan porque la base las exige para PENDIENTE_REVISION y ACTIVA.
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
}
