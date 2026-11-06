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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta de las publicaciones propias del Anfitrión. PostgreSQL real con las migraciones y catálogos de V2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsultaPublicacionesPropiasIntegrationTest {

    private static final String URL = "/api/v1/publicaciones/mias";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario otroAnfitrion;

    @Autowired
    ConsultaPublicacionesPropiasIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate,
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
        otroAnfitrion = crearUsuario("otro.anfitrion@correo.com", Rol.ANFITRION);
    }

    @Test
    @DisplayName("Criterio 1: se muestran únicamente las publicaciones asociadas a la cuenta")
    void listar_muestraSoloLasPropias() throws Exception {
        insertarPublicacion(anfitrion, "Casa propia 1", "BORRADOR");
        insertarPublicacion(otroAnfitrion, "Casa ajena", "ACTIVA");
        insertarPublicacion(anfitrion, "Casa propia 2", "BORRADOR");

        listar(anfitrion, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].titulo", containsInAnyOrder("Casa propia 1", "Casa propia 2")))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    @DisplayName("Criterio 2: con publicaciones en diferentes estados se muestra el estado vigente de cada una")
    void listar_muestraElEstadoVigenteDeCadaUna() throws Exception {
        insertarPublicacion(anfitrion, "En borrador", "BORRADOR");
        insertarPublicacion(anfitrion, "En revisión", "PENDIENTE_REVISION");
        insertarPublicacion(anfitrion, "Activa", "ACTIVA");
        insertarPublicacion(anfitrion, "Pausada", "PAUSADA");
        insertarPublicacion(anfitrion, "Bloqueada", "BLOQUEADA");

        listar(anfitrion, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(5)))
                .andExpect(jsonPath("$.contenido[*].titulo",
                        contains("Bloqueada", "Pausada", "Activa", "En revisión", "En borrador")))
                .andExpect(jsonPath("$.contenido[*].estado",
                        contains("BLOQUEADA", "PAUSADA", "ACTIVA", "PENDIENTE_REVISION", "BORRADOR")))
                .andExpect(jsonPath("$.contenido[0].ciudad.nombre").value("Cartagena"))
                .andExpect(jsonPath("$.contenido[0].tipo.nombre").value("Apartamento"))
                .andExpect(jsonPath("$.contenido[0].capacidad").value(4))
                .andExpect(jsonPath("$.contenido[0].precioNoche").value(250000.00));
    }

    @Test
    @DisplayName("Criterio 3: sin publicaciones se informa que no hay y no aparecen las de otros Anfitriones")
    void listar_sinPublicaciones_informaSinMostrarLasDeOtros() throws Exception {
        insertarPublicacion(otroAnfitrion, "Casa ajena", "ACTIVA");

        listar(anfitrion, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.mensaje").value("No tiene publicaciones registradas"));
    }

    @Test
    void listar_pagina_devuelveElTrozoPedidoConLosTotales() throws Exception {
        for (int i = 1; i <= 5; i++) {
            insertarPublicacion(anfitrion, "Casa " + i, "BORRADOR");
        }

        listar(anfitrion, "?pagina=1&tamano=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[*].titulo", contains("Casa 3", "Casa 2")))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3));
    }

    @Test
    void listar_sinParametros_usaPaginaCeroYTamanoVeinte() throws Exception {
        listar(anfitrion, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamano").value(20));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "?tamano=0, tamano",
            "?tamano=101, tamano",
            "?pagina=-1, pagina",
            "?pagina=abc, pagina"
    })
    void listar_parametrosInvalidos_responde400EnElCampo(String consulta, String campo) throws Exception {
        listar(anfitrion, consulta)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ADMINISTRADOR"})
    void listar_otroRol_responde403YRegistraIntento(String rol) throws Exception {
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@correo.com", Rol.valueOf(rol));

        listar(usuario, "").andExpect(status().isForbidden());

        Integer denegados = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'ACCESO_DENEGADO'", Integer.class);
        assertThat(denegados).isEqualTo(1);
    }

    @Test
    void listar_sinToken_responde401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void contratoOpenApi_documentaListarPropias() throws Exception {
        String ruta = "$.paths['" + URL + "'].get";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ruta + ".responses['200']", notNullValue()))
                .andExpect(jsonPath(ruta + ".responses['400']", notNullValue()))
                .andExpect(jsonPath(ruta + ".responses['403']", notNullValue()));
    }

    private ResultActions listar(Usuario usuario, String consulta) throws Exception {
        return mockMvc.perform(get(URL + consulta).header(HttpHeaders.AUTHORIZATION, token(usuario)));
    }

    /**
     * Inserta directamente en la base: así se prueban estados a los que todavía ningún endpoint lleva. Las reglas
     * se llenan porque la base las exige para PENDIENTE_REVISION y ACTIVA.
     */
    private void insertarPublicacion(Usuario propietario, String titulo, String estado) {
        jdbcTemplate.update("""
                INSERT INTO publicacion (id_anfitrion, id_ciudad, id_tipo, id_politica, titulo, descripcion,
                                         direccion_textual, capacidad, precio_noche, tarifa_limpieza, reglas, estado)
                VALUES (?, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                        (SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'),
                        (SELECT id_politica FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        ?, 'Descripción de prueba', 'Calle 5 # 3-20', 4, 250000.00, 0, 'Reglas de prueba', ?)
                """, propietario.getIdUsuario(), titulo, estado);
    }

    private String token(Usuario usuario) {
        return "Bearer " + tokenService.generar(usuario).token();
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.save(new Usuario(new NombreCompleto("Nombre", null, "Apellido", null),
                correo, "hash-no-usado", rol));
    }
}
