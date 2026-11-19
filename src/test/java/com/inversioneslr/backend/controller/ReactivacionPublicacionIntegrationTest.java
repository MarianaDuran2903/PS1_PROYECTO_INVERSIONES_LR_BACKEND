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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reactivación por el Administrador de una publicación pausada o bloqueada. PostgreSQL real con las
 * migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReactivacionPublicacionIntegrationTest {

    private static final String URL = "/api/v1/admin/publicaciones";
    private static final String URL_ANFITRION = "/api/v1/publicaciones";
    private static final String URL_ALOJAMIENTOS = "/api/v1/alojamientos";
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
    ReactivacionPublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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

    // ---------------------------------------------------------------- Criterio 5: pausada

    @Test
    @DisplayName("Criterio 5: el Administrador reactiva una publicación PAUSADA y queda ACTIVA")
    void reactivar_pausada_quedaActiva() throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");

        reactivar(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(id))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.motivoBloqueo").doesNotExist());

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Criterio 5: la reactivación de una pausada queda registrada con el origen y el motivo")
    void reactivar_pausada_registraLaDecision() throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");

        reactivar(administrador, id, "  " + MOTIVO + "  ").andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'REACTIVAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(id))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", "PAUSADA -> ACTIVA: " + MOTIVO)
                .containsEntry("correo", "admin@correo.com");
    }

    @Test
    @DisplayName("Criterio 5: una vez reactivada, la pausada vuelve a ofrecerse en la búsqueda pública")
    void reactivar_pausada_vuelveALaBusqueda() throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");
        mockMvc.perform(get(URL_ALOJAMIENTOS)).andExpect(jsonPath("$.contenido", hasSize(0)));

        reactivar(administrador, id, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(get(URL_ALOJAMIENTOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    @DisplayName("Flujo: el Anfitrión pausa y ajusta, y el Administrador revisa y la reactiva")
    void pausadaYAjustada_elAdministradorLaReactiva() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        mockMvc.perform(post(URL_ANFITRION + "/" + id + "/pausar")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADA"));
        editarComoAnfitrion(id, "Casa uno ajustada").andExpect(status().isOk());

        reactivar(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.titulo").value("Casa uno ajustada"));

        mockMvc.perform(get(URL_ALOJAMIENTOS))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].titulo").value("Casa uno ajustada"));
    }

    // ---------------------------------------------------------------- Criterio 6: bloqueada

    @Test
    @DisplayName("Criterio 6: el Administrador reactiva una BLOQUEADA y el motivo del bloqueo se limpia")
    void reactivar_bloqueada_quedaActivaYLimpiaElMotivo() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(id, MOTIVO_BLOQUEO).andExpect(status().isOk());
        assertThat(motivoEnBase(id)).isEqualTo(MOTIVO_BLOQUEO);

        reactivar(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.motivoBloqueo").doesNotExist());

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
        assertThat(motivoEnBase(id)).isNull();
    }

    @Test
    @DisplayName("Criterio 6: la reactivación de una bloqueada queda registrada y el bloqueo sigue en la auditoría")
    void reactivar_bloqueada_registraLaDecisionYConservaElHistorial() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(id, MOTIVO_BLOQUEO).andExpect(status().isOk());

        reactivar(administrador, id, MOTIVO).andExpect(status().isOk());

        assertThat(detalleDelEvento("REACTIVAR_PUBLICACION")).isEqualTo("BLOQUEADA -> ACTIVA: " + MOTIVO);
        assertThat(detalleDelEvento("BLOQUEAR_PUBLICACION")).isEqualTo(MOTIVO_BLOQUEO);
    }

    @Test
    @DisplayName("Criterio 6: el Administrador lee el motivo del bloqueo antes de decidir y vuelve a ofrecerla")
    void bloqueada_elAdministradorLeeElMotivoYLaReactiva() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(id, MOTIVO_BLOQUEO).andExpect(status().isOk());
        mockMvc.perform(get(URL_ALOJAMIENTOS)).andExpect(jsonPath("$.contenido", hasSize(0)));

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.motivoBloqueo").value(MOTIVO_BLOQUEO));
        reactivar(administrador, id, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(get(URL_ALOJAMIENTOS))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id));
    }

    @Test
    @DisplayName("Una bloqueada anterior a la columna del motivo, sin motivo, también se reactiva")
    void reactivar_bloqueadaSinMotivoGuardado_quedaActiva() throws Exception {
        long id = publicacion("Casa uno", "BLOQUEADA");
        assertThat(motivoEnBase(id)).isNull();

        reactivar(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    @DisplayName("Reactivada, la publicación vuelve a ser del Anfitrión: puede pausarla otra vez")
    void reactivada_elAnfitrionPuedePausarla() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(id, MOTIVO_BLOQUEO).andExpect(status().isOk());
        reactivar(administrador, id, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(post(URL_ANFITRION + "/" + id + "/pausar")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADA"));
    }

    // ---------------------------------------------------------------- Motivo y datos

    @ParameterizedTest(name = "motivo [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"corto", "          "})
    @DisplayName("Sin un motivo válido responde 400 y la publicación no cambia")
    void reactivar_motivoInvalido_responde400YConservaElEstado(String motivo) throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");

        reactivar(administrador, id, motivo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("PAUSADA");
        assertThat(eventos("REACTIVAR_PUBLICACION")).isZero();
    }

    @Test
    void reactivar_motivoMuyLargo_responde400YConservaElEstado() throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");

        reactivar(administrador, id, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("PAUSADA");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"PAUSADA", "BLOQUEADA"})
    @DisplayName("Sin la información mínima responde 400 con los pendientes y conserva el estado")
    void reactivar_sinInformacionMinima_responde400YConservaElEstado(String estado) throws Exception {
        long id = publicacion("Casa uno", estado);
        jdbcTemplate.update("UPDATE publicacion SET reglas = NULL, barrio_zona = NULL WHERE id_publicacion = ?", id);

        reactivar(administrador, id, MOTIVO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La publicación no tiene la información mínima para activarse"))
                .andExpect(jsonPath("$.errores.reglas", notNullValue()))
                .andExpect(jsonPath("$.errores.barrioZona", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo(estado);
        assertThat(eventos("REACTIVAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("Si no se puede reactivar, la bloqueada conserva el motivo del bloqueo")
    void reactivar_bloqueadaSinInformacionMinima_conservaElMotivo() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(id, MOTIVO_BLOQUEO).andExpect(status().isOk());
        jdbcTemplate.update("UPDATE publicacion SET reglas = NULL WHERE id_publicacion = ?", id);

        reactivar(administrador, id, MOTIVO).andExpect(status().isBadRequest());

        assertThat(estadoEnBase(id)).isEqualTo("BLOQUEADA");
        assertThat(motivoEnBase(id)).isEqualTo(MOTIVO_BLOQUEO);
    }

    // ---------------------------------------------------------------- Estados que no se reactivan

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "ACTIVA"})
    @DisplayName("Fuera de PAUSADA y BLOQUEADA responde 409 y no cambia el estado")
    void reactivar_otroEstado_responde409(String estado) throws Exception {
        long id = publicacion("Casa uno", estado);

        reactivar(administrador, id, MOTIVO).andExpect(status().isConflict());

        assertThat(estadoEnBase(id)).isEqualTo(estado);
        assertThat(eventos("REACTIVAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("Reactivar dos veces la misma publicación: la segunda se rechaza y queda un solo evento")
    void reactivar_dosVeces_laSegundaResponde409() throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");

        reactivar(administrador, id, MOTIVO).andExpect(status().isOk());
        reactivar(administrador, id, MOTIVO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación ya está activa"));

        assertThat(eventos("REACTIVAR_PUBLICACION")).isEqualTo(1);
    }

    @Test
    void reactivar_publicacionInexistente_responde404() throws Exception {
        reactivar(administrador, 987654, MOTIVO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    @Test
    void reactivar_identificadorNoNumerico_responde400() throws Exception {
        mockMvc.perform(post(URL + "/abc/reactivar")
                        .header(HttpHeaders.AUTHORIZATION, token(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Acceso

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Otro rol no puede reactivar, la publicación no cambia y se registra el intento")
    void reactivar_otroRol_responde403(String rol) throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@otro.com", Rol.valueOf(rol));

        reactivar(usuario, id, MOTIVO).andExpect(status().isForbidden());

        assertThat(estadoEnBase(id)).isEqualTo("PAUSADA");
        assertThat(eventos("ACCESO_DENEGADO")).isEqualTo(1);
        assertThat(eventos("REACTIVAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("El propio Anfitrión no puede reactivar su publicación bloqueada")
    void reactivar_elPropietarioAnfitrion_responde403() throws Exception {
        long id = publicacion("Casa uno", "BLOQUEADA");

        reactivar(anfitrion, id, MOTIVO).andExpect(status().isForbidden());

        assertThat(estadoEnBase(id)).isEqualTo("BLOQUEADA");
    }

    @Test
    void reactivar_sinToken_responde401() throws Exception {
        long id = publicacion("Casa uno", "PAUSADA");

        mockMvc.perform(post(URL + "/" + id + "/reactivar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(estadoEnBase(id)).isEqualTo("PAUSADA");
    }

    @Test
    void contratoOpenApi_documentaLaReactivacion() throws Exception {
        String reactivar = "$.paths['" + URL + "/{idPublicacion}/reactivar'].post.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(reactivar + "['200']", notNullValue()))
                .andExpect(jsonPath(reactivar + "['400']", notNullValue()))
                .andExpect(jsonPath(reactivar + "['401']", notNullValue()))
                .andExpect(jsonPath(reactivar + "['403']", notNullValue()))
                .andExpect(jsonPath(reactivar + "['404']", notNullValue()))
                .andExpect(jsonPath(reactivar + "['409']", notNullValue()));
    }

    // ---------------------------------------------------------------- Apoyo

    private ResultActions reactivar(Usuario usuario, long id, String motivo) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(post(URL + "/" + id + "/reactivar")
                .header(HttpHeaders.AUTHORIZATION, token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions bloquear(long id, String motivo) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(post(URL + "/" + id + "/bloquear")
                .header(HttpHeaders.AUTHORIZATION, token(administrador))
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

    private String motivoEnBase(long id) {
        return jdbcTemplate.queryForObject("SELECT motivo_bloqueo FROM publicacion WHERE id_publicacion = ?",
                String.class, id);
    }

    private String detalleDelEvento(String accion) {
        return jdbcTemplate.queryForObject("SELECT detalle FROM evento_auditoria WHERE accion = ?",
                String.class, accion);
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
