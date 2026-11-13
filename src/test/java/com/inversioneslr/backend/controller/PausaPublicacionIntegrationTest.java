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
 * Pausa de una publicación activa por su Anfitrión. PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PausaPublicacionIntegrationTest {

    private static final String URL = "/api/v1/publicaciones";
    private static final String URL_BUSQUEDA = "/api/v1/alojamientos";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private long idPublicacion;

    @Autowired
    PausaPublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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
        ponerEstado("ACTIVA");
    }

    @Test
    @DisplayName("Criterio 1: el Anfitrión propietario pasa su publicación ACTIVA a PAUSADA")
    void pausar_activaPropia_pasaAPausada() throws Exception {
        pausar(anfitrion, idPublicacion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(idPublicacion))
                .andExpect(jsonPath("$.estado").value("PAUSADA"));

        assertThat(estadoEnBase()).isEqualTo("PAUSADA");
    }

    @Test
    @DisplayName("Pausar no elimina la información de la publicación")
    void pausar_conservaLaInformacion() throws Exception {
        pausar(anfitrion, idPublicacion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Apartamento con vista al mar"))
                .andExpect(jsonPath("$.descripcion").value("Dos habitaciones, cocina equipada y balcón."))
                .andExpect(jsonPath("$.direccionTextual").value("Calle 5 # 3-20"))
                .andExpect(jsonPath("$.reglas").value("No se admiten fiestas"))
                .andExpect(jsonPath("$.precioNoche").value(250000.00));

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT titulo, descripcion, direccion_textual, reglas FROM publicacion WHERE id_publicacion = ?",
                idPublicacion);
        assertThat(fila)
                .containsEntry("titulo", "Apartamento con vista al mar")
                .containsEntry("direccion_textual", "Calle 5 # 3-20")
                .containsEntry("reglas", "No se admiten fiestas");
    }

    @Test
    @DisplayName("Criterio 2: la publicación pausada deja de presentarse en la búsqueda pública")
    void pausar_laPublicacionDejaDeAparecerEnLaBusqueda() throws Exception {
        mockMvc.perform(get(URL_BUSQUEDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(idPublicacion));

        pausar(anfitrion, idPublicacion).andExpect(status().isOk());

        mockMvc.perform(get(URL_BUSQUEDA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.mensaje").value("No hay alojamientos disponibles por el momento"));
    }

    @Test
    @DisplayName("Criterio 2: tampoco la ve un Huésped autenticado")
    void pausar_laPublicacionDejaDeAparecerParaUnHuesped() throws Exception {
        Usuario huesped = crearUsuario("huesped@correo.com", Rol.HUESPED);

        pausar(anfitrion, idPublicacion).andExpect(status().isOk());

        mockMvc.perform(get(URL_BUSQUEDA).header(HttpHeaders.AUTHORIZATION, token(huesped)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    @DisplayName("Pausar una publicación no oculta las demás del Anfitrión")
    void pausar_noAfectaALasOtrasPublicaciones() throws Exception {
        long otra = crearPublicacion(anfitrion);
        ponerEstado(otra, "ACTIVA");

        pausar(anfitrion, idPublicacion).andExpect(status().isOk());

        mockMvc.perform(get(URL_BUSQUEDA))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idPublicacion").value(otra));
    }

    @Test
    @DisplayName("Criterio 4: la pausa queda registrada para trazabilidad")
    void pausar_exitosa_registraEventoDeAuditoria() throws Exception {
        pausar(anfitrion, idPublicacion).andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'PAUSAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(idPublicacion))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", "ACTIVA -> PAUSADA")
                .containsEntry("correo", "anfitrion@correo.com");
    }

    @Test
    @DisplayName("Criterio 3: una publicación de otro Anfitrión se deniega y no cambia")
    void pausar_publicacionAjena_responde403YConservaElEstado() throws Exception {
        Usuario intruso = crearUsuario("intruso@correo.com", Rol.ANFITRION);

        pausar(intruso, idPublicacion).andExpect(status().isForbidden());

        assertThat(estadoEnBase()).isEqualTo("ACTIVA");
        mockMvc.perform(get(URL_BUSQUEDA)).andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("Criterio 3: el intento sobre una publicación ajena queda registrado y no deja una pausa")
    void pausar_publicacionAjena_registraElIntentoSinPausa() throws Exception {
        Usuario intruso = crearUsuario("intruso@correo.com", Rol.ANFITRION);

        pausar(intruso, idPublicacion).andExpect(status().isForbidden());

        Map<String, Object> intento = jdbcTemplate.queryForMap("""
                SELECT e.resultado, e.id_entidad, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'ACCESO_DENEGADO'
                """);
        assertThat(intento)
                .containsEntry("resultado", "FALLIDO")
                .containsEntry("id_entidad", String.valueOf(idPublicacion))
                .containsEntry("correo", "intruso@correo.com");
        Integer pausas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'PAUSAR_PUBLICACION'", Integer.class);
        assertThat(pausas).isZero();
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Fuera de ACTIVA responde 409, no cambia el estado y no registra una pausa")
    void pausar_estadoDistintoDeActiva_responde409(String estado) throws Exception {
        ponerEstado(estado);

        pausar(anfitrion, idPublicacion).andExpect(status().isConflict());

        assertThat(estadoEnBase()).isEqualTo(estado);
        Integer exitosos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'PAUSAR_PUBLICACION'", Integer.class);
        assertThat(exitosos).isZero();
    }

    @Test
    void pausar_publicacionInexistente_responde404() throws Exception {
        pausar(anfitrion, idPublicacion + 1000)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    @Test
    void pausar_identificadorNoNumerico_responde400() throws Exception {
        mockMvc.perform(post(URL + "/abc/pausar").header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ADMINISTRADOR"})
    void pausar_otroRol_responde403YConservaElEstado(String rol) throws Exception {
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@correo.com", Rol.valueOf(rol));

        pausar(usuario, idPublicacion).andExpect(status().isForbidden());

        assertThat(estadoEnBase()).isEqualTo("ACTIVA");
    }

    @Test
    void pausar_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL + "/" + idPublicacion + "/pausar"))
                .andExpect(status().isUnauthorized());

        assertThat(estadoEnBase()).isEqualTo("ACTIVA");
    }

    @Test
    @DisplayName("Una segunda pausa sobre la misma publicación se rechaza")
    void pausar_dosVeces_laSegundaResponde409() throws Exception {
        pausar(anfitrion, idPublicacion).andExpect(status().isOk());

        pausar(anfitrion, idPublicacion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación ya está pausada"));

        Integer pausas = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'PAUSAR_PUBLICACION'", Integer.class);
        assertThat(pausas).isEqualTo(1);
    }

    @Test
    void contratoOpenApi_documentaPausar() throws Exception {
        String ruta = "$.paths['" + URL + "/{idPublicacion}/pausar'].post.responses";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ruta + "['200']", notNullValue()))
                .andExpect(jsonPath(ruta + "['400']", notNullValue()))
                .andExpect(jsonPath(ruta + "['401']", notNullValue()))
                .andExpect(jsonPath(ruta + "['403']", notNullValue()))
                .andExpect(jsonPath(ruta + "['404']", notNullValue()))
                .andExpect(jsonPath(ruta + "['409']", notNullValue()));
    }

    /**
     * La creación deja la publicación en BORRADOR y sin reglas; la base exige reglas para PENDIENTE_REVISION y
     * ACTIVA, así que se completan junto con el estado.
     */
    private void ponerEstado(String estado) {
        ponerEstado(idPublicacion, estado);
    }

    private void ponerEstado(long id, String estado) {
        jdbcTemplate.update("UPDATE publicacion SET reglas = 'No se admiten fiestas', estado = ? "
                + "WHERE id_publicacion = ?", estado, id);
    }

    private String estadoEnBase() {
        return jdbcTemplate.queryForObject("SELECT estado FROM publicacion WHERE id_publicacion = ?",
                String.class, idPublicacion);
    }

    private ResultActions pausar(Usuario usuario, long id) throws Exception {
        return mockMvc.perform(post(URL + "/" + id + "/pausar")
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
