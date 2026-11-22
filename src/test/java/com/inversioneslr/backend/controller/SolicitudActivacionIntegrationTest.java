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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Solicitud de activación de una publicación por su Anfitrión. PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SolicitudActivacionIntegrationTest {

    private static final String URL = "/api/v1/publicaciones";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private long idPublicacion;

    @Autowired
    SolicitudActivacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                                       UsuarioRepository usuarioRepository, TokenService tokenService) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    @BeforeEach
    void preparar() throws Exception {
        BaseDeDatosDePrueba.limpiar(jdbcTemplate);
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        idPublicacion = crearPublicacion(anfitrion);
    }

    @Test
    @DisplayName("Criterios 1 y 2: una publicación completa en BORRADOR pasa a PENDIENTE_REVISION")
    void solicitar_publicacionCompleta_pasaARevision() throws Exception {
        completarInformacion();

        solicitar(anfitrion, idPublicacion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(idPublicacion))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_REVISION"));

        assertThat(estadoEnBase()).isEqualTo("PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("Criterio 4: la transición queda registrada para trazabilidad")
    void solicitar_exitosa_registraEventoDeAuditoria() throws Exception {
        completarInformacion();

        solicitar(anfitrion, idPublicacion).andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'ENVIAR_A_REVISION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(idPublicacion))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", "BORRADOR -> PENDIENTE_REVISION")
                .containsEntry("correo", "anfitrion@correo.com");
    }

    @Test
    @DisplayName("Criterio 3: sin reglas informa el pendiente y conserva el estado")
    void solicitar_sinReglas_responde400YConservaElEstado() throws Exception {
        jdbcTemplate.update("UPDATE publicacion SET reglas = NULL WHERE id_publicacion = ?", idPublicacion);

        solicitar(anfitrion, idPublicacion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La publicación no está lista para revisión"))
                .andExpect(jsonPath("$.errores.reglas").value("Las reglas son obligatorias para solicitar la activación"))
                .andExpect(jsonPath("$.errores.barrioZona").doesNotExist());

        assertThat(estadoEnBase()).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Criterio 3: informa todos los pendientes a la vez")
    void solicitar_variosFaltantes_losInformaTodos() throws Exception {
        jdbcTemplate.update("UPDATE publicacion SET reglas = NULL, barrio_zona = NULL WHERE id_publicacion = ?",
                idPublicacion);

        solicitar(anfitrion, idPublicacion)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.reglas", notNullValue()))
                .andExpect(jsonPath("$.errores.barrioZona", notNullValue()));

        assertThat(estadoEnBase()).isEqualTo("BORRADOR");
    }

    @Test
    @DisplayName("Criterio 3: el rechazo por información faltante no deja un evento de envío a revisión")
    void solicitar_sinInformacion_noRegistraEventoDeEnvio() throws Exception {
        solicitar(anfitrion, idPublicacion).andExpect(status().isBadRequest());

        Integer eventos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'ENVIAR_A_REVISION'", Integer.class);
        assertThat(eventos).isZero();
        assertThat(estadoEnBase()).isEqualTo("BORRADOR");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"PENDIENTE_REVISION", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Fuera de BORRADOR responde 409 y no cambia el estado")
    void solicitar_estadoDistintoDeBorrador_responde409(String estado) throws Exception {
        completarInformacion();
        jdbcTemplate.update("UPDATE publicacion SET estado = ? WHERE id_publicacion = ?", estado, idPublicacion);

        solicitar(anfitrion, idPublicacion).andExpect(status().isConflict());

        assertThat(estadoEnBase()).isEqualTo(estado);
        Integer exitosos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'ENVIAR_A_REVISION' AND resultado = 'EXITOSO'",
                Integer.class);
        assertThat(exitosos).isZero();
    }

    @Test
    @DisplayName("Una publicación de otro Anfitrión se deniega y se registra el intento")
    void solicitar_publicacionAjena_responde403() throws Exception {
        completarInformacion();
        Usuario intruso = crearUsuario("intruso@correo.com", Rol.ANFITRION);

        solicitar(intruso, idPublicacion).andExpect(status().isForbidden());

        assertThat(estadoEnBase()).isEqualTo("BORRADOR");
        Integer denegados = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'ACCESO_DENEGADO'", Integer.class);
        assertThat(denegados).isEqualTo(1);
    }

    @Test
    void solicitar_publicacionInexistente_responde404() throws Exception {
        solicitar(anfitrion, idPublicacion + 1000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    @Test
    void solicitar_identificadorNoNumerico_responde400() throws Exception {
        mockMvc.perform(post(URL + "/abc/enviar-revision").header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ADMINISTRADOR"})
    void solicitar_otroRol_responde403(String rol) throws Exception {
        completarInformacion();
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@correo.com", Rol.valueOf(rol));

        solicitar(usuario, idPublicacion).andExpect(status().isForbidden());

        assertThat(estadoEnBase()).isEqualTo("BORRADOR");
    }

    @Test
    void solicitar_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL + "/" + idPublicacion + "/enviar-revision"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Una segunda solicitud sobre la misma publicación se rechaza")
    void solicitar_dosVeces_laSegundaResponde409() throws Exception {
        completarInformacion();

        solicitar(anfitrion, idPublicacion).andExpect(status().isOk());
        solicitar(anfitrion, idPublicacion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación ya está en revisión"));
    }

    @Test
    void contratoOpenApi_documentaEnviarARevision() throws Exception {
        String ruta = "$.paths['" + URL + "/{idPublicacion}/enviar-revision'].post.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ruta + "['200']", notNullValue()))
                .andExpect(jsonPath(ruta + "['400']", notNullValue()))
                .andExpect(jsonPath(ruta + "['403']", notNullValue()))
                .andExpect(jsonPath(ruta + "['404']", notNullValue()))
                .andExpect(jsonPath(ruta + "['409']", notNullValue()));
    }

    /**
     * La creación deja el barrio pero no las reglas; se completan directamente en la base.
     */
    private void completarInformacion() {
        jdbcTemplate.update("UPDATE publicacion SET reglas = 'No se admiten fiestas' WHERE id_publicacion = ?",
                idPublicacion);
    }

    private String estadoEnBase() {
        return jdbcTemplate.queryForObject("SELECT estado FROM publicacion WHERE id_publicacion = ?",
                String.class, idPublicacion);
    }

    private ResultActions solicitar(Usuario usuario, long id) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/enviar-revision")
                .header(HttpHeaders.AUTHORIZATION, token(usuario)));
    }

    private long crearPublicacion(Usuario propietario) throws Exception {
        long idCiudad = idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'");
        long idTipo = idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'");
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", "Apartamento con vista al mar");
        cuerpo.put("descripcion", "Dos habitaciones, cocina equipada y balcón.");
        cuerpo.put("direccionTextual", "Calle 5 # 3-20");
        cuerpo.put("idCiudad", idCiudad);
        cuerpo.put("barrioZona", "Bocagrande");
        cuerpo.put("idTipo", idTipo);
        cuerpo.put("capacidad", 4);
        cuerpo.put("precioNoche", new BigDecimal("250000.00"));
        String respuesta = mockMvc.perform(post(URL)
                        .header(HttpHeaders.AUTHORIZATION, token(propietario))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("idPublicacion").asLong();
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
