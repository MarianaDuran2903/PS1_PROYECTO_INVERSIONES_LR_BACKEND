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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Intervención del Administrador sobre publicaciones. PostgreSQL real con las migraciones, incluida V4.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IntervencionAdminPublicacionIntegrationTest {

    private static final String URL = "/api/v1/admin/publicaciones";
    private static final String TITULO_ORIGINAL = "Apartamento con vista al mar";
    private static final String MOTIVO = "Corrección de la dirección reportada por el Anfitrión";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario admin;
    private Usuario anfitrion;
    private long idMedellin;
    private long idCasa;

    @Autowired
    IntervencionAdminPublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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
        admin = crearUsuario("admin@correo.com", Rol.ADMINISTRADOR);
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        idMedellin = idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Medellín'");
        idCasa = idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Casa'");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Criterio 1: el Administrador guarda los cambios sin modificar el estado ni el propietario")
    void intervenir_estadoPermitido_guardaCambios(String estado) throws Exception {
        long id = insertarPublicacion(estado);

        intervenir(admin, id, cuerpo(MOTIVO, "No se admiten mascotas."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Casa campestre corregida"))
                .andExpect(jsonPath("$.ciudad.nombre").value("Medellín"))
                .andExpect(jsonPath("$.tipo.nombre").value("Casa"))
                .andExpect(jsonPath("$.reglas").value("No se admiten mascotas."))
                .andExpect(jsonPath("$.estado").value(estado))
                .andExpect(jsonPath("$.idAnfitrion").value(anfitrion.getIdUsuario()));

        assertThat(fila(id))
                .containsEntry("titulo", "Casa campestre corregida")
                .containsEntry("estado", estado)
                .containsEntry("id_anfitrion", anfitrion.getIdUsuario());
    }

    @Test
    @DisplayName("Criterio 3: queda registrado quién intervino, cuándo y bajo qué motivo")
    void intervenir_registraQuienCuandoYMotivo() throws Exception {
        long id = insertarPublicacion("PAUSADA");

        intervenir(admin, id, cuerpo(MOTIVO, null)).andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, e.detalle, e.fecha_hora, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'INTERVENCION_ADMIN_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(id))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("detalle", MOTIVO)
                .containsEntry("correo", "admin@correo.com");
        assertThat(evento.get("fecha_hora")).isNotNull();
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"ANFITRION", "HUESPED"})
    @DisplayName("Criterio 2: un usuario sin rol Administrador recibe acceso denegado y se registra el intento")
    void intervenir_otroRol_responde403SinCambios(String rol) throws Exception {
        long id = insertarPublicacion("PAUSADA");
        Usuario usuario = rol.equals("ANFITRION") ? anfitrion : crearUsuario("huesped@correo.com", Rol.HUESPED);

        intervenir(usuario, id, cuerpo(MOTIVO, null)).andExpect(status().isForbidden());

        assertThat(fila(id)).containsEntry("titulo", TITULO_ORIGINAL);
        Integer denegados = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'ACCESO_DENEGADO'", Integer.class);
        assertThat(denegados).isEqualTo(1);
    }

    @Test
    @DisplayName("Criterio 4: con datos inválidos se informan los errores y no se aplica ningún cambio")
    void intervenir_datosInvalidos_responde400SinCambios() throws Exception {
        long id = insertarPublicacion("PAUSADA");
        Map<String, Object> cuerpo = cuerpo(MOTIVO, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> cambios = (Map<String, Object>) cuerpo.get("cambios");
        cambios.put("capacidad", 0);
        cambios.put("titulo", " ");

        intervenir(admin, id, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['cambios.capacidad']", notNullValue()))
                .andExpect(jsonPath("$.errores['cambios.titulo']", notNullValue()));

        assertSinIntervencion(id);
    }

    @ParameterizedTest(name = "motivo \"{0}\"")
    @ValueSource(strings = {"", "corto", "   "})
    @DisplayName("Criterio 4: sin un motivo de 10 a 500 caracteres no se aplica ningún cambio")
    void intervenir_motivoInvalido_responde400SinCambios(String motivo) throws Exception {
        long id = insertarPublicacion("PAUSADA");

        intervenir(admin, id, cuerpo(motivo, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo", notNullValue()));

        assertSinIntervencion(id);
    }

    @Test
    void intervenir_motivoDeMasDe500Caracteres_responde400() throws Exception {
        long id = insertarPublicacion("PAUSADA");

        intervenir(admin, id, cuerpo("m".repeat(501), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.motivo").value("El motivo debe tener entre 10 y 500 caracteres"));
    }

    @Test
    void intervenir_sinCambios_responde400() throws Exception {
        long id = insertarPublicacion("PAUSADA");

        intervenir(admin, id, Map.of("motivo", MOTIVO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.cambios").value("Los cambios de la publicación son obligatorios"));
    }

    @Test
    @DisplayName("Criterio 4: una publicación en revisión no puede quedar sin reglas")
    void intervenir_enRevisionSinReglas_responde400SinCambios() throws Exception {
        long id = insertarPublicacion("PENDIENTE_REVISION");

        intervenir(admin, id, cuerpo(MOTIVO, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['cambios.reglas']")
                        .value("Las reglas son obligatorias para una publicación en revisión"));

        assertSinIntervencion(id);
    }

    @Test
    void intervenir_ciudadInexistente_responde400SinCambios() throws Exception {
        long id = insertarPublicacion("PAUSADA");
        Map<String, Object> cuerpo = cuerpo(MOTIVO, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> cambios = (Map<String, Object>) cuerpo.get("cambios");
        cambios.put("idCiudad", 99999);

        intervenir(admin, id, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['cambios.idCiudad']").value("La ciudad no existe"));

        assertSinIntervencion(id);
    }

    @Test
    @DisplayName("Una publicación activa se pausa antes de intervenirla")
    void intervenir_publicacionActiva_responde409SinCambios() throws Exception {
        long id = insertarPublicacion("ACTIVA");

        intervenir(admin, id, cuerpo(MOTIVO, "Reglas nuevas"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación está activa: páusela antes de intervenirla"));

        assertThat(fila(id)).containsEntry("estado", "ACTIVA");
        assertSinIntervencion(id);
    }

    @Test
    void intervenir_publicacionInexistente_responde404() throws Exception {
        intervenir(admin, 999999, cuerpo(MOTIVO, null)).andExpect(status().isNotFound());
    }

    @Test
    void intervenir_sinToken_responde401() throws Exception {
        mockMvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo(MOTIVO, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El Administrador consulta la publicación de cualquier Anfitrión")
    void obtener_administrador_devuelveLaPublicacion() throws Exception {
        long id = insertarPublicacion("ACTIVA");

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value(TITULO_ORIGINAL))
                .andExpect(jsonPath("$.idAnfitrion").value(anfitrion.getIdUsuario()))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    void obtener_anfitrionPropietario_responde403() throws Exception {
        long id = insertarPublicacion("BORRADOR");

        mockMvc.perform(get(URL + "/" + id).header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_publicacionInexistente_responde404() throws Exception {
        mockMvc.perform(get(URL + "/999999").header(HttpHeaders.AUTHORIZATION, token(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void contratoOpenApi_documentaIntervencion() throws Exception {
        String ruta = "$.paths['" + URL + "/{idPublicacion}']";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ruta + ".get.responses['200']", notNullValue()))
                .andExpect(jsonPath(ruta + ".put.responses['200']", notNullValue()))
                .andExpect(jsonPath(ruta + ".put.responses['403']", notNullValue()))
                .andExpect(jsonPath(ruta + ".put.responses['409']", notNullValue()));
    }

    private void assertSinIntervencion(long id) {
        assertThat(fila(id)).containsEntry("titulo", TITULO_ORIGINAL);
        Integer intervenciones = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'INTERVENCION_ADMIN_PUBLICACION'",
                Integer.class);
        assertThat(intervenciones).isZero();
    }

    private Map<String, Object> cuerpo(String motivo, String reglas) {
        Map<String, Object> cambios = new LinkedHashMap<>();
        cambios.put("titulo", "Casa campestre corregida");
        cambios.put("descripcion", "Tres habitaciones, patio y chimenea.");
        cambios.put("direccionTextual", "Carrera 7 # 10-15");
        cambios.put("idCiudad", idMedellin);
        cambios.put("barrioZona", null);
        cambios.put("idTipo", idCasa);
        cambios.put("capacidad", 6);
        cambios.put("precioNoche", new BigDecimal("320000.00"));
        cambios.put("tarifaLimpieza", new BigDecimal("40000.00"));
        cambios.put("reglas", reglas);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("motivo", motivo);
        cuerpo.put("cambios", cambios);
        return cuerpo;
    }

    private ResultActions intervenir(Usuario usuario, long id, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(put(URL + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    /**
     * Inserta directamente en la base para probar estados a los que todavía ningún endpoint lleva. Las reglas
     * se llenan porque la base las exige para PENDIENTE_REVISION y ACTIVA.
     */
    private long insertarPublicacion(String estado) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO publicacion (id_anfitrion, id_ciudad, id_tipo, id_politica, titulo, descripcion,
                                         direccion_textual, capacidad, precio_noche, tarifa_limpieza, reglas, estado)
                VALUES (?, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                        (SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'),
                        (SELECT id_politica FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        ?, 'Dos habitaciones', 'Calle 5 # 3-20', 4, 250000.00, 0, 'Reglas originales', ?)
                RETURNING id_publicacion
                """, Long.class, anfitrion.getIdUsuario(), TITULO_ORIGINAL, estado);
        assertThat(id).isNotNull();
        return id;
    }

    private Map<String, Object> fila(long id) {
        return jdbcTemplate.queryForMap(
                "SELECT titulo, estado, id_anfitrion FROM publicacion WHERE id_publicacion = ?", id);
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
