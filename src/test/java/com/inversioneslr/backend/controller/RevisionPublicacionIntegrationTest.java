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
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revisión de publicaciones pendientes por el Administrador. PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RevisionPublicacionIntegrationTest {

    private static final String URL = "/api/v1/admin/publicaciones";
    private static final String URL_ANFITRION = "/api/v1/publicaciones";
    private static final String MOTIVO = "Las fotos y la dirección no coinciden con la descripción";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario administrador;

    @Autowired
    RevisionPublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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

    // ---------------------------------------------------------------- Consultar pendientes

    @Test
    @DisplayName("Criterio 1: lista solo las pendientes, de la más antigua a la más reciente")
    void listar_devuelveSoloLasPendientesEnOrdenDeLlegada() throws Exception {
        long primera = publicacion("Casa uno", "PENDIENTE_REVISION");
        publicacion("Casa borrador", "BORRADOR");
        long segunda = publicacion("Casa dos", "PENDIENTE_REVISION");
        publicacion("Casa activa", "ACTIVA");
        publicacion("Casa pausada", "PAUSADA");
        publicacion("Casa bloqueada", "BLOQUEADA");

        listar(administrador, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(primera))
                .andExpect(jsonPath("$.contenido[1].idPublicacion").value(segunda))
                .andExpect(jsonPath("$.contenido[*].estado", everyItem(is("PENDIENTE_REVISION"))))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    @DisplayName("Criterio 1: cada elemento trae la información necesaria para decidir")
    void listar_incluyeLaInformacionCompletaDeCadaPublicacion() throws Exception {
        publicacion("Casa uno", "PENDIENTE_REVISION");

        listar(administrador, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido[0].titulo").value("Casa uno"))
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

    @Test
    @DisplayName("Criterio 1: sin pendientes la lista viene vacía y el mensaje lo informa")
    void listar_sinPendientes_informaElMensaje() throws Exception {
        publicacion("Casa borrador", "BORRADOR");

        listar(administrador, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(0))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.mensaje").value("No hay publicaciones pendientes de revisión"));
    }

    @Test
    void listar_pagina_devuelveElTrozoPedidoConLosTotales() throws Exception {
        publicacion("Casa uno", "PENDIENTE_REVISION");
        publicacion("Casa dos", "PENDIENTE_REVISION");
        long tercera = publicacion("Casa tres", "PENDIENTE_REVISION");

        listar(administrador, "?pagina=1&tamano=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(tercera))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamano").value(2))
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.totalPaginas").value(2));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "?tamano=0, tamano",
            "?tamano=101, tamano",
            "?pagina=-1, pagina",
            "?pagina=abc, pagina"
    })
    void listar_parametrosInvalidos_responde400EnElCampo(String consulta, String campo) throws Exception {
        listar(administrador, consulta)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    // ---------------------------------------------------------------- Aprobar

    @Test
    @DisplayName("Criterio 2: aprobar una pendiente la deja ACTIVA")
    void aprobar_pendiente_quedaActiva() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        aprobar(administrador, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(id))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Criterio 2: la decisión queda registrada con el Administrador")
    void aprobar_registraLaDecision() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        aprobar(administrador, id).andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'APROBAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(id))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", "PENDIENTE_REVISION -> ACTIVA")
                .containsEntry("correo", "admin@correo.com");
    }

    @Test
    @DisplayName("Aprobar sin la información mínima responde 400 con los pendientes y conserva el estado")
    void aprobar_sinInformacionMinima_responde400YConservaElEstado() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");
        jdbcTemplate.update("UPDATE publicacion SET barrio_zona = NULL WHERE id_publicacion = ?", id);

        aprobar(administrador, id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La publicación no tiene la información mínima para activarse"))
                .andExpect(jsonPath("$.errores.barrioZona", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("PENDIENTE_REVISION");
        assertThat(eventos("APROBAR_PUBLICACION")).isZero();
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Aprobar fuera de PENDIENTE_REVISION responde 409 y no cambia el estado")
    void aprobar_estadoDistintoDePendiente_responde409(String estado) throws Exception {
        long id = publicacion("Casa uno", estado);

        aprobar(administrador, id).andExpect(status().isConflict());

        assertThat(estadoEnBase(id)).isEqualTo(estado);
        assertThat(eventos("APROBAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("Aprobar dos veces la misma publicación: la segunda se rechaza")
    void aprobar_dosVeces_laSegundaResponde409() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        aprobar(administrador, id).andExpect(status().isOk());
        aprobar(administrador, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación ya está activa"));

        assertThat(eventos("APROBAR_PUBLICACION")).isEqualTo(1);
    }

    @Test
    void aprobar_publicacionInexistente_responde404() throws Exception {
        aprobar(administrador, 987654)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    @Test
    void aprobar_identificadorNoNumerico_responde400() throws Exception {
        mockMvc.perform(post(URL + "/abc/aprobar").header(HttpHeaders.AUTHORIZATION, token(administrador)))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Rechazar

    @Test
    @DisplayName("Criterio 3: rechazar una pendiente la devuelve a BORRADOR")
    void rechazar_pendiente_vuelveABorrador() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        rechazar(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(id))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        assertThat(estadoEnBase(id)).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Criterio 3: el motivo queda registrado con el Administrador")
    void rechazar_registraElMotivo() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        rechazar(administrador, id, "  " + MOTIVO + "  ").andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'RECHAZAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(id))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", MOTIVO)
                .containsEntry("correo", "admin@correo.com");
    }

    @Test
    @DisplayName("Una pendiente sin barrio también puede rechazarse")
    void rechazar_pendienteSinInformacionMinima_seRechaza() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");
        jdbcTemplate.update("UPDATE publicacion SET barrio_zona = NULL WHERE id_publicacion = ?", id);

        rechazar(administrador, id, MOTIVO).andExpect(status().isOk());

        assertThat(estadoEnBase(id)).isEqualTo("BORRADOR");
    }

    @ParameterizedTest(name = "motivo [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"corto", "          "})
    @DisplayName("Criterio 3: sin un motivo válido responde 400 y la publicación no cambia")
    void rechazar_motivoInvalido_responde400YConservaElEstado(String motivo) throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        rechazar(administrador, id, motivo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("PENDIENTE_REVISION");
        assertThat(eventos("RECHAZAR_PUBLICACION")).isZero();
    }

    @Test
    void rechazar_motivoMuyLargo_responde400() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        rechazar(administrador, id, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Rechazar fuera de PENDIENTE_REVISION responde 409 y no cambia el estado")
    void rechazar_estadoDistintoDePendiente_responde409(String estado) throws Exception {
        long id = publicacion("Casa uno", estado);

        rechazar(administrador, id, MOTIVO).andExpect(status().isConflict());

        assertThat(estadoEnBase(id)).isEqualTo(estado);
        assertThat(eventos("RECHAZAR_PUBLICACION")).isZero();
    }

    @Test
    void rechazar_publicacionInexistente_responde404() throws Exception {
        rechazar(administrador, 987654, MOTIVO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    // ---------------------------------------------------------------- Flujo completo

    @Test
    @DisplayName("Una publicación rechazada se corrige, se reenvía y el Administrador la aprueba")
    void rechazada_elAnfitrionLaCorrigeLaReenviaYSeAprueba() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");
        rechazar(administrador, id, MOTIVO).andExpect(status().isOk());

        editarComoAnfitrion(id, "Casa uno corregida").andExpect(status().isOk());
        mockMvc.perform(post(URL_ANFITRION + "/" + id + "/enviar-revision")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));
        listar(administrador, "")
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Casa uno corregida"));

        aprobar(administrador, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
    }

    // ---------------------------------------------------------------- Acceso

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Criterio 4: otro rol no puede listar pendientes y se registra el intento")
    void listar_otroRol_responde403(String rol) throws Exception {
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@otro.com", Rol.valueOf(rol));

        listar(usuario, "").andExpect(status().isForbidden());

        assertThat(eventos("ACCESO_DENEGADO")).isEqualTo(1);
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Criterio 4: otro rol no puede aprobar y la publicación no cambia")
    void aprobar_otroRol_responde403(String rol) throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@otro.com", Rol.valueOf(rol));

        aprobar(usuario, id).andExpect(status().isForbidden());

        assertThat(estadoEnBase(id)).isEqualTo("PENDIENTE_REVISION");
        assertThat(eventos("ACCESO_DENEGADO")).isEqualTo(1);
        assertThat(eventos("APROBAR_PUBLICACION")).isZero();
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Criterio 4: otro rol no puede rechazar y la publicación no cambia")
    void rechazar_otroRol_responde403(String rol) throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@otro.com", Rol.valueOf(rol));

        rechazar(usuario, id, MOTIVO).andExpect(status().isForbidden());

        assertThat(estadoEnBase(id)).isEqualTo("PENDIENTE_REVISION");
        assertThat(eventos("ACCESO_DENEGADO")).isEqualTo(1);
        assertThat(eventos("RECHAZAR_PUBLICACION")).isZero();
    }

    @Test
    void sinToken_responde401EnLasTresOperaciones() throws Exception {
        long id = publicacion("Casa uno", "PENDIENTE_REVISION");

        mockMvc.perform(get(URL + "/pendientes")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/" + id + "/aprobar")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(URL + "/" + id + "/rechazar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(estadoEnBase(id)).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    void contratoOpenApi_documentaLasTresOperaciones() throws Exception {
        String pendientes = "$.paths['" + URL + "/pendientes'].get.responses";
        String aprobar = "$.paths['" + URL + "/{idPublicacion}/aprobar'].post.responses";
        String rechazar = "$.paths['" + URL + "/{idPublicacion}/rechazar'].post.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(pendientes + "['200']", notNullValue()))
                .andExpect(jsonPath(pendientes + "['403']", notNullValue()))
                .andExpect(jsonPath(aprobar + "['200']", notNullValue()))
                .andExpect(jsonPath(aprobar + "['400']", notNullValue()))
                .andExpect(jsonPath(aprobar + "['403']", notNullValue()))
                .andExpect(jsonPath(aprobar + "['404']", notNullValue()))
                .andExpect(jsonPath(aprobar + "['409']", notNullValue()))
                .andExpect(jsonPath(rechazar + "['200']", notNullValue()))
                .andExpect(jsonPath(rechazar + "['400']", notNullValue()))
                .andExpect(jsonPath(rechazar + "['403']", notNullValue()))
                .andExpect(jsonPath(rechazar + "['404']", notNullValue()))
                .andExpect(jsonPath(rechazar + "['409']", notNullValue()));
    }

    // ---------------------------------------------------------------- Apoyo

    private ResultActions listar(Usuario usuario, String consulta) throws Exception {
        return mockMvc.perform(get(URL + "/pendientes" + consulta)
                .header(HttpHeaders.AUTHORIZATION, token(usuario)));
    }

    private ResultActions aprobar(Usuario usuario, long id) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/aprobar")
                .header(HttpHeaders.AUTHORIZATION, token(usuario)));
    }

    private ResultActions rechazar(Usuario usuario, long id, String motivo) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(post(URL + "/" + id + "/rechazar")
                .header(HttpHeaders.AUTHORIZATION, token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions editarComoAnfitrion(long id, String titulo) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", titulo);
        cuerpo.put("descripcion", "Dos habitaciones, cocina equipada y balcón.");
        cuerpo.put("direccionTextual", "Calle 5 # 3-20");
        cuerpo.put("idCiudad", idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'"));
        cuerpo.put("barrioZona", "Bocagrande");
        cuerpo.put("idTipo", idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'"));
        cuerpo.put("capacidad", 4);
        cuerpo.put("precioNoche", new BigDecimal("250000.00"));
        cuerpo.put("tarifaLimpieza", new BigDecimal("30000.00"));
        cuerpo.put("reglas", "No se admiten fiestas");
        return mockMvc.perform(put(URL_ANFITRION + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
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

    private String estadoEnBase(long id) {
        return jdbcTemplate.queryForObject("SELECT estado FROM publicacion WHERE id_publicacion = ?",
                String.class, id);
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
