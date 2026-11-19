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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloqueo de una publicación engañosa por el Administrador. PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BloqueoPublicacionIntegrationTest {

    private static final String URL = "/api/v1/admin/publicaciones";
    private static final String URL_ANFITRION = "/api/v1/publicaciones";
    private static final String URL_ALOJAMIENTOS = "/api/v1/alojamientos";
    private static final String MOTIVO = "Las fotos pertenecen a otro alojamiento y la dirección no existe";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario administrador;

    @Autowired
    BloqueoPublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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

    // ---------------------------------------------------------------- Criterio 1: cambia a BLOQUEADA

    @Test
    @DisplayName("Criterio 1: bloquear una publicación ACTIVA la deja BLOQUEADA")
    void bloquear_activa_quedaBloqueada() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(id))
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));

        assertThat(estadoEnBase(id)).isEqualTo("BLOQUEADA");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"PENDIENTE_REVISION", "PAUSADA"})
    @DisplayName("Criterio 1: también se bloquea una publicación en revisión o pausada")
    void bloquear_enRevisionOPausada_quedaBloqueada(String estado) throws Exception {
        long id = publicacion("Casa uno", estado);

        bloquear(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));

        assertThat(estadoEnBase(id)).isEqualTo("BLOQUEADA");
    }

    @Test
    @DisplayName("Bloquear conserva toda la información de la publicación")
    void bloquear_conservaLaInformacion() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa uno"))
                .andExpect(jsonPath("$.descripcion").value("Dos habitaciones, cocina equipada y balcón."))
                .andExpect(jsonPath("$.direccionTextual").value("Calle 5 # 3-20"))
                .andExpect(jsonPath("$.reglas").value("No se admiten fiestas"))
                .andExpect(jsonPath("$.precioNoche").value(250000.00))
                .andExpect(jsonPath("$.idAnfitrion").value(anfitrion.getIdUsuario()));
    }

    @Test
    @DisplayName("Flujo: el Anfitrión pausa y el Administrador, al no aprobar el ajuste, la bloquea")
    void pausadaPorElAnfitrion_elAdministradorLaBloquea() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        mockMvc.perform(post(URL_ANFITRION + "/" + id + "/pausar")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADA"));

        bloquear(administrador, id, MOTIVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"));

        assertThat(estadoEnBase(id)).isEqualTo("BLOQUEADA");
    }

    // ---------------------------------------------------------------- Criterio 2: motivo y auditoría

    @Test
    @DisplayName("Criterio 2: el motivo queda registrado con el Administrador en la auditoría")
    void bloquear_registraElMotivoYElEvento() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, "  " + MOTIVO + "  ").andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, e.fecha_hora, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'BLOQUEAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(id))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", MOTIVO)
                .containsEntry("correo", "admin@correo.com");
        assertThat(evento.get("fecha_hora")).isNotNull();
    }

    @Test
    @DisplayName("Criterio 2: el motivo se guarda en la publicación y la respuesta lo devuelve")
    void bloquear_guardaElMotivoEnLaPublicacion() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, "  " + MOTIVO + "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoBloqueo").value(MOTIVO));

        assertThat(motivoEnBase(id)).isEqualTo(MOTIVO);
    }

    @Test
    @DisplayName("El Administrador y el Anfitrión propietario consultan el motivo del bloqueo")
    void bloquear_elAdministradorYElAnfitrionLeenElMotivo() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(administrador, id, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.motivoBloqueo").value(MOTIVO));
        mockMvc.perform(get(URL_ANFITRION + "/" + id).header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("BLOQUEADA"))
                .andExpect(jsonPath("$.motivoBloqueo").value(MOTIVO));
    }

    @Test
    @DisplayName("Una publicación que no está bloqueada no trae motivo de bloqueo")
    void consultar_publicacionNoBloqueada_noTraeMotivo() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token(administrador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoBloqueo").doesNotExist());
    }

    @Test
    @DisplayName("La base solo admite un motivo de bloqueo en una publicación BLOQUEADA")
    void base_motivoDeBloqueoEnUnaPublicacionNoBloqueada_seRechaza() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE publicacion SET motivo_bloqueo = ? WHERE id_publicacion = ?", MOTIVO, id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "motivo [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {"corto", "          "})
    @DisplayName("Criterio 2: sin un motivo válido responde 400 y la publicación no cambia")
    void bloquear_motivoInvalido_responde400YConservaElEstado(String motivo) throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, motivo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
        assertThat(motivoEnBase(id)).isNull();
        assertThat(eventos("BLOQUEAR_PUBLICACION")).isZero();
    }

    @Test
    void bloquear_motivoMuyLargo_responde400YConservaElEstado() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, "x".repeat(501))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
        assertThat(eventos("BLOQUEAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("El motivo de 500 caracteres, el máximo, se acepta completo")
    void bloquear_motivoDelLargoMaximo_seRegistraCompleto() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        String motivo = "x".repeat(500);

        bloquear(administrador, id, motivo).andExpect(status().isOk());

        String detalle = jdbcTemplate.queryForObject(
                "SELECT detalle FROM evento_auditoria WHERE accion = 'BLOQUEAR_PUBLICACION'", String.class);
        assertThat(detalle).isEqualTo(motivo);
    }

    @Test
    void bloquear_sinCuerpo_responde400() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        mockMvc.perform(post(URL + "/" + id + "/bloquear")
                        .header(HttpHeaders.AUTHORIZATION, token(administrador))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
    }

    // ---------------------------------------------------------------- Estados que no se bloquean

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "BLOQUEADA"})
    @DisplayName("Un borrador o una publicación ya bloqueada responde 409 y no cambia")
    void bloquear_borradorOBloqueada_responde409(String estado) throws Exception {
        long id = publicacion("Casa uno", estado);

        bloquear(administrador, id, MOTIVO).andExpect(status().isConflict());

        assertThat(estadoEnBase(id)).isEqualTo(estado);
        assertThat(eventos("BLOQUEAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("Bloquear dos veces la misma publicación: la segunda se rechaza y queda un solo evento")
    void bloquear_dosVeces_laSegundaResponde409() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, MOTIVO).andExpect(status().isOk());
        bloquear(administrador, id, MOTIVO)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación ya está bloqueada"));

        assertThat(eventos("BLOQUEAR_PUBLICACION")).isEqualTo(1);
    }

    @Test
    void bloquear_publicacionInexistente_responde404() throws Exception {
        bloquear(administrador, 987654, MOTIVO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    @Test
    void bloquear_identificadorNoNumerico_responde400() throws Exception {
        mockMvc.perform(post(URL + "/abc/bloquear")
                        .header(HttpHeaders.AUTHORIZATION, token(administrador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Criterio 3: no se ofrece

    @Test
    @DisplayName("Criterio 3: un Visitante ya no ve la publicación bloqueada y sigue viendo las demás")
    void bloquear_laPublicacionDejaDeOfrecerseAlVisitante() throws Exception {
        long bloqueada = publicacion("Casa engañosa", "ACTIVA");
        long vigente = publicacion("Casa vigente", "ACTIVA");
        mockMvc.perform(get(URL_ALOJAMIENTOS))
                .andExpect(jsonPath("$.contenido", hasSize(2)));

        bloquear(administrador, bloqueada, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(get(URL_ALOJAMIENTOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(vigente))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("Criterio 3: un Huésped autenticado tampoco la ve como disponible")
    void bloquear_laPublicacionDejaDeOfrecerseAlHuesped() throws Exception {
        Usuario huesped = crearUsuario("huesped@correo.com", Rol.HUESPED);
        long id = publicacion("Casa engañosa", "ACTIVA");

        bloquear(administrador, id, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(get(URL_ALOJAMIENTOS).header(HttpHeaders.AUTHORIZATION, token(huesped)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(0)))
                .andExpect(jsonPath("$.mensaje").value("No hay alojamientos disponibles por el momento"));
    }

    // ---------------------------------------------------------------- Efecto sobre el Anfitrión

    @Test
    @DisplayName("El Anfitrión sigue viendo su publicación bloqueada en su listado")
    void bloquear_elAnfitrionLaVeBloqueadaEnSuListado() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(administrador, id, MOTIVO).andExpect(status().isOk());

        mockMvc.perform(get(URL_ANFITRION + "/mias").header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(id))
                .andExpect(jsonPath("$.contenido[0].estado").value("BLOQUEADA"));
    }

    @Test
    @DisplayName("Bloqueada, el Anfitrión no puede editarla, pausarla ni enviarla a revisión")
    void bloqueada_elAnfitrionNoPuedeModificarla() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        bloquear(administrador, id, MOTIVO).andExpect(status().isOk());

        editarComoAnfitrion(id).andExpect(status().isConflict());
        mockMvc.perform(post(URL_ANFITRION + "/" + id + "/pausar")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isConflict());
        mockMvc.perform(post(URL_ANFITRION + "/" + id + "/enviar-revision")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isConflict());

        assertThat(estadoEnBase(id)).isEqualTo("BLOQUEADA");
    }

    // ---------------------------------------------------------------- Criterio 4: acceso

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ANFITRION"})
    @DisplayName("Criterio 4: otro rol no puede bloquear, la publicación no cambia y se registra el intento")
    void bloquear_otroRol_responde403(String rol) throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@otro.com", Rol.valueOf(rol));

        bloquear(usuario, id, MOTIVO).andExpect(status().isForbidden());

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
        assertThat(eventos("ACCESO_DENEGADO")).isEqualTo(1);
        assertThat(eventos("BLOQUEAR_PUBLICACION")).isZero();
    }

    @Test
    @DisplayName("Criterio 4: el propio Anfitrión no puede bloquear su publicación")
    void bloquear_elPropietarioAnfitrion_responde403() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        bloquear(anfitrion, id, MOTIVO).andExpect(status().isForbidden());

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
        assertThat(eventos("BLOQUEAR_PUBLICACION")).isZero();
    }

    @Test
    void bloquear_sinToken_responde401() throws Exception {
        long id = publicacion("Casa uno", "ACTIVA");

        mockMvc.perform(post(URL + "/" + id + "/bloquear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"" + MOTIVO + "\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(estadoEnBase(id)).isEqualTo("ACTIVA");
    }

    @Test
    void contratoOpenApi_documentaElBloqueo() throws Exception {
        String bloquear = "$.paths['" + URL + "/{idPublicacion}/bloquear'].post.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(bloquear + "['200']", notNullValue()))
                .andExpect(jsonPath(bloquear + "['400']", notNullValue()))
                .andExpect(jsonPath(bloquear + "['401']", notNullValue()))
                .andExpect(jsonPath(bloquear + "['403']", notNullValue()))
                .andExpect(jsonPath(bloquear + "['404']", notNullValue()))
                .andExpect(jsonPath(bloquear + "['409']", notNullValue()));
    }

    // ---------------------------------------------------------------- Apoyo

    private ResultActions bloquear(Usuario usuario, long id, String motivo) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        return mockMvc.perform(post(URL + "/" + id + "/bloquear")
                .header(HttpHeaders.AUTHORIZATION, token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions editarComoAnfitrion(long id) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", "Casa uno corregida");
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
