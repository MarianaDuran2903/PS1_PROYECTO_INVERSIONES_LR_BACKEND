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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Listado de publicaciones por estado para el Administrador. PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ListadoAdminPublicacionesIntegrationTest {

    private static final String URL = "/api/v1/admin/publicaciones";
    private static final String URL_ANFITRION = "/api/v1/publicaciones";
    private static final String MOTIVO_BLOQUEO = "Las fotos pertenecen a otro alojamiento y la dirección no existe";
    private static final String MOTIVO = "El Anfitrión corrigió las fotos y la dirección, y se verificaron";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario administrador;

    @Autowired
    ListadoAdminPublicacionesIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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
        administrador = crearUsuario("admin@correo.com", Rol.ADMINISTRADOR);
    }

    // ---------------------------------------------------------------- Sin filtro

    @Test
    @DisplayName("Sin estado lista las publicaciones de todos los estados, la más reciente primero")
    void listar_sinEstado_devuelveTodasLaMasRecientePrimero() throws Exception {
        long borrador = publicacion("Casa borrador", "BORRADOR");
        long pendiente = publicacion("Casa pendiente", "PENDIENTE_REVISION");
        long activa = publicacion("Casa activa", "ACTIVA");
        long pausada = publicacion("Casa pausada", "PAUSADA");
        long bloqueada = publicacion("Casa bloqueada", "BLOQUEADA");

        listar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(5)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(bloqueada))
                .andExpect(jsonPath("$.contenido[1].idPublicacion").value(pausada))
                .andExpect(jsonPath("$.contenido[2].idPublicacion").value(activa))
                .andExpect(jsonPath("$.contenido[3].idPublicacion").value(pendiente))
                .andExpect(jsonPath("$.contenido[4].idPublicacion").value(borrador))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    @DisplayName("Cada elemento trae la información completa y el Anfitrión propietario")
    void listar_incluyeLaInformacionCompleta() throws Exception {
        publicacion("Casa uno", "PAUSADA");

        listar("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].titulo").value("Casa uno"))
                .andExpect(jsonPath("$.contenido[0].estado").value("PAUSADA"))
                .andExpect(jsonPath("$.contenido[0].descripcion").value("Dos habitaciones, cocina equipada y balcón."))
                .andExpect(jsonPath("$.contenido[0].direccionTextual").value("Calle 5 # 3-20"))
                .andExpect(jsonPath("$.contenido[0].barrioZona").value("Bocagrande"))
                .andExpect(jsonPath("$.contenido[0].reglas").value("No se admiten fiestas"))
                .andExpect(jsonPath("$.contenido[0].ciudad.nombre").value("Cartagena"))
                .andExpect(jsonPath("$.contenido[0].tipo.nombre").value("Apartamento"))
                .andExpect(jsonPath("$.contenido[0].politica.nombre").value("Flexible"))
                .andExpect(jsonPath("$.contenido[0].precioNoche").value(250000.00))
                .andExpect(jsonPath("$.contenido[0].idAnfitrion").value(anfitrion.getIdUsuario()));
    }

    // ---------------------------------------------------------------- Por estado

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Con un estado lista solo las publicaciones de ese estado")
    void listar_conEstado_devuelveSoloEseEstado(String estado) throws Exception {
        long esperada = publicacion("Casa del estado", estado);
        for (String otro : new String[]{"BORRADOR", "PENDIENTE_REVISION", "ACTIVA", "PAUSADA", "BLOQUEADA"}) {
            if (!otro.equals(estado)) {
                publicacion("Casa " + otro, otro);
            }
        }

        listar("?estado=" + estado)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(esperada))
                .andExpect(jsonPath("$.contenido[*].estado", everyItem(is(estado))))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("Una bloqueada trae el motivo del bloqueo; las demás lo traen nulo")
    void listar_bloqueada_traeElMotivoDelBloqueo() throws Exception {
        long id = publicacion("Casa engañosa", "ACTIVA");
        publicacion("Casa vigente", "PAUSADA");
        bloquear(id).andExpect(status().isOk());

        listar("?estado=BLOQUEADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id))
                .andExpect(jsonPath("$.contenido[0].motivoBloqueo").value(MOTIVO_BLOQUEO));
        listar("?estado=PAUSADA")
                .andExpect(jsonPath("$.contenido[0].motivoBloqueo").doesNotExist());
    }

    @Test
    @DisplayName("El Administrador encuentra una pausada en la lista y la reactiva")
    void listar_pausada_seEncuentraYSeReactiva() throws Exception {
        long id = publicacion("Casa pausada", "PAUSADA");

        listar("?estado=PAUSADA")
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
        reactivar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACTIVA"));

        listar("?estado=PAUSADA").andExpect(jsonPath("$.contenido", hasSize(0)));
        listar("?estado=ACTIVA")
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    @DisplayName("Un estado sin publicaciones devuelve la lista vacía con un mensaje")
    void listar_estadoSinPublicaciones_informaElMensaje() throws Exception {
        publicacion("Casa activa", "ACTIVA");

        listar("?estado=BLOQUEADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.mensaje").value("No hay publicaciones para mostrar"));
    }

    // ---------------------------------------------------------------- Paginación y parámetros

    @Test
    void listar_pagina_devuelveElTrozoPedidoConLosTotales() throws Exception {
        publicacion("Casa uno", "PAUSADA");
        publicacion("Casa dos", "PAUSADA");
        long tercera = publicacion("Casa tres", "PAUSADA");
        publicacion("Casa activa", "ACTIVA");

        listar("?estado=PAUSADA&pagina=1&tamano=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Casa uno"))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        listar("?estado=PAUSADA&pagina=0&tamano=2")
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(tercera));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "?tamano=0, tamano",
            "?tamano=101, tamano",
            "?pagina=-1, pagina",
            "?pagina=abc, pagina",
            "?estado=INEXISTENTE, estado",
            "?estado=activa, estado"
    })
    void listar_parametrosInvalidos_responde400EnElCampo(String consulta, String campo) throws Exception {
        listar(consulta)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    @Test
    @DisplayName("La ruta de pendientes sigue funcionando junto al nuevo listado")
    void pendientes_siguenFuncionando() throws Exception {
        long id = publicacion("Casa pendiente", "PENDIENTE_REVISION");
        publicacion("Casa pausada", "PAUSADA");

        mockMvc.perform(get(URL + "/pendientes").header(HttpHeaders.AUTHORIZATION, token(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    // ---------------------------------------------------------------- Acceso

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Otro rol no puede listar y se registra el intento")
    void listar_otroRol_responde403(String rol) throws Exception {
        publicacion("Casa uno", "BLOQUEADA");
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@otro.com", Rol.valueOf(rol));

        mockMvc.perform(get(URL).header(HttpHeaders.AUTHORIZATION, token(usuario)))
                .andExpect(status().isForbidden());

        assertThat(eventos("ACCESO_DENEGADO")).isEqualTo(1);
    }

    @Test
    void listar_sinToken_responde401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void contratoOpenApi_documentaElListado() throws Exception {
        String listar = "$.paths['" + URL + "'].get.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(listar + "['200']", notNullValue()))
                .andExpect(jsonPath(listar + "['400']", notNullValue()))
                .andExpect(jsonPath(listar + "['401']", notNullValue()))
                .andExpect(jsonPath(listar + "['403']", notNullValue()));
    }

    // ---------------------------------------------------------------- Apoyo

    private ResultActions listar(String consulta) throws Exception {
        return mockMvc.perform(get(URL + consulta).header(HttpHeaders.AUTHORIZATION, token(administrador)));
    }

    private ResultActions bloquear(long id) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", MOTIVO_BLOQUEO);
        return mockMvc.perform(post(URL + "/" + id + "/bloquear")
                .header(HttpHeaders.AUTHORIZATION, token(administrador))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions reactivar(long id) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", MOTIVO);
        return mockMvc.perform(post(URL + "/" + id + "/reactivar")
                .header(HttpHeaders.AUTHORIZATION, token(administrador))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    /**
     * Crea la publicación por la API del Anfitrión y la deja en el estado pedido con las reglas completas,
     * porque la creación no las registra.
     */
    private long publicacion(String titulo, String estado) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", titulo);
        cuerpo.put("descripcion", "Dos habitaciones, cocina equipada y balcón.");
        cuerpo.put("direccionTextual", "Calle 5 # 3-20");
        cuerpo.put("idCiudad", idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'"));
        cuerpo.put("barrioZona", "Bocagrande");
        cuerpo.put("idTipo", idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'"));
        cuerpo.put("capacidad", 4);
        cuerpo.put("precioNoche", new BigDecimal("250000.00"));
        String respuesta = mockMvc.perform(post(URL_ANFITRION)
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(respuesta).get("idPublicacion").asLong();
        jdbcTemplate.update("UPDATE publicacion SET reglas = 'No se admiten fiestas', estado = ? "
                + "WHERE id_publicacion = ?", estado, id);
        return id;
    }

    private Integer eventos(String accion) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM evento_auditoria WHERE accion = ?",
                Integer.class, accion);
    }

    private String token(Usuario usuario) {
        return "Bearer " + tokenService.generar(usuario).token();
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.save(new Usuario(new NombreCompleto("Nombre", null, "Apellido", null),
                correo, "hash-no-usado", rol));
    }

    private long idCatalogo(String consulta) {
        Long id = jdbcTemplate.queryForObject(consulta, Long.class);
        assertThat(id).isNotNull();
        return id;
    }
}
