package com.inversioneslr.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-003: iniciar sesión según rol. Usa PostgreSQL real (Testcontainers) y los endpoints de prueba por rol.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InicioSesionIntegrationTest {

    private static final String URL_LOGIN = "/api/v1/auth/login";
    private static final String URL_SESION = "/api/v1/auth/sesion";
    private static final String CONTRASENA = "Segura#123";
    private static final String MENSAJE_GENERICO = "Correo o contraseña incorrectos";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;
    private final EventoAuditoriaRepository eventoAuditoriaRepository;

    @Autowired
    InicioSesionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
                                PasswordEncoder passwordEncoder, UsuarioRepository usuarioRepository,
                                EventoAuditoriaRepository eventoAuditoriaRepository) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
        this.eventoAuditoriaRepository = eventoAuditoriaRepository;
    }

    @BeforeEach
    void prepararUsuarios() {
        eventoAuditoriaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
        crearUsuario("huesped@correo.com", Rol.HUESPED);
        crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        crearUsuario("admin@correo.com", Rol.ADMINISTRADOR);
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource({"huesped@correo.com, HUESPED", "anfitrion@correo.com, ANFITRION", "admin@correo.com, ADMINISTRADOR"})
    @DisplayName("Criterio 1: con credenciales válidas autentica y crea una sesión activa con su rol")
    void login_credencialesValidas_creaSesion(String correo, String rol) throws Exception {
        login(correo, CONTRASENA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value(3600))
                .andExpect(jsonPath("$.usuario.correo").value(correo))
                .andExpect(jsonPath("$.usuario.rol").value(rol));

        assertThat(contarEventos("INICIO_SESION", "EXITOSO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Criterio 1: la sesión queda activa: el token permite consultar /sesion")
    void sesion_conTokenValido_devuelveUsuarioYRol() throws Exception {
        String token = obtenerToken("anfitrion@correo.com");

        mockMvc.perform(get(URL_SESION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.correo").value("anfitrion@correo.com"))
                .andExpect(jsonPath("$.usuario.rol").value("ANFITRION"))
                .andExpect(jsonPath("$.expiraEn", notNullValue()));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "contraseña incorrecta, huesped@correo.com, Otra#1234",
            "correo inexistente,    nadie@correo.com,   Segura#123"
    })
    @DisplayName("Criterio 2: credenciales inválidas → mensaje genérico, sin sesión y con registro del intento")
    void login_credencialesInvalidas_mensajeGenericoSinToken(String caso, String correo, String contrasena)
            throws Exception {
        login(correo, contrasena)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_GENERICO))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(contarEventos("INICIO_SESION", "FALLIDO")).isEqualTo(1);
    }

    @Test
    @DisplayName("Criterio 2: un correo inexistente queda registrado sin usuario")
    void login_correoInexistente_eventoSinUsuario() throws Exception {
        login("nadie@correo.com", CONTRASENA).andExpect(status().isUnauthorized());

        Integer sinUsuario = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'INICIO_SESION' AND id_usuario IS NULL",
                Integer.class);
        assertThat(sinUsuario).isEqualTo(1);
    }

    @Test
    @DisplayName("Una cuenta desactivada recibe el mismo mensaje genérico")
    void login_cuentaDesactivada_mensajeGenerico() throws Exception {
        jdbcTemplate.update("UPDATE usuario SET cuenta_activa = false WHERE correo = 'huesped@correo.com'");

        login("huesped@correo.com", CONTRASENA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_GENERICO));
    }

    @Test
    @DisplayName("Faltan datos en el login → 400")
    void login_sinDatos_responde400() throws Exception {
        mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo", notNullValue()))
                .andExpect(jsonPath("$.errores.contrasena", notNullValue()));
    }

    @ParameterizedTest(name = "{1} accede a {2}")
    @CsvSource({
            "huesped@correo.com,   HUESPED,       /api/v1/prueba-roles/huesped",
            "anfitrion@correo.com, ANFITRION,     /api/v1/prueba-roles/anfitrion",
            "admin@correo.com,     ADMINISTRADOR, /api/v1/prueba-roles/administrador"
    })
    @DisplayName("Criterios 3, 4 y 5: cada rol usa las funcionalidades autorizadas para su perfil")
    void cadaRol_accedeASuFuncionalidad(String correo, String rol, String ruta) throws Exception {
        String token = obtenerToken(correo);

        mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0} pide {1}")
    @CsvSource({
            "huesped@correo.com,   /api/v1/prueba-roles/anfitrion",
            "huesped@correo.com,   /api/v1/prueba-roles/administrador",
            "anfitrion@correo.com, /api/v1/prueba-roles/huesped",
            "anfitrion@correo.com, /api/v1/prueba-roles/administrador",
            "admin@correo.com,     /api/v1/prueba-roles/huesped",
            "admin@correo.com,     /api/v1/prueba-roles/anfitrion"
    })
    @DisplayName("Criterio 6: una funcionalidad no autorizada para el rol se deniega y se registra el intento")
    void rolNoAutorizado_deniegaYRegistraIntento(String correo, String ruta) throws Exception {
        String token = obtenerToken(correo);

        mockMvc.perform(get(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tiene permisos para esta operación"));

        List<Map<String, Object>> eventos = jdbcTemplate.queryForList("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'ACCESO_DENEGADO'
                """);
        assertThat(eventos).singleElement().satisfies(evento -> assertThat(evento)
                .containsEntry("tipo_entidad", "Ruta")
                .containsEntry("id_entidad", "GET " + ruta)
                .containsEntry("resultado", "FALLIDO")
                .containsEntry("correo", correo));
    }

    @Test
    @DisplayName("Sin token, una ruta protegida responde 401")
    void sinToken_responde401() throws Exception {
        mockMvc.perform(get(URL_SESION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesión"));
    }

    @Test
    @DisplayName("Un token inválido o alterado responde 401")
    void tokenInvalido_responde401() throws Exception {
        String token = obtenerToken("huesped@correo.com");

        mockMvc.perform(get(URL_SESION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URL_SESION).header(HttpHeaders.AUTHORIZATION, "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Si la cuenta se desactiva después del login, el token deja de servir")
    void tokenDeCuentaDesactivada_responde401() throws Exception {
        String token = obtenerToken("huesped@correo.com");
        jdbcTemplate.update("UPDATE usuario SET cuenta_activa = false WHERE correo = 'huesped@correo.com'");

        mockMvc.perform(get(URL_SESION).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS: el frontend en http://localhost:4200 puede llamar a la API con Authorization")
    void cors_permiteElFrontendLocal() throws Exception {
        mockMvc.perform(options(URL_SESION)
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));
    }

    @Test
    @DisplayName("CORS: un origen desconocido se rechaza")
    void cors_rechazaOrigenDesconocido() throws Exception {
        mockMvc.perform(options(URL_SESION)
                        .header(HttpHeaders.ORIGIN, "http://sitio-malicioso.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El contrato OpenAPI documenta login, sesión y el esquema bearer")
    void contratoOpenApi_documentaLoginYSesion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + URL_LOGIN + "'].post.responses['401']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + URL_SESION + "'].get.responses['200']", notNullValue()))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    private void crearUsuario(String correo, Rol rol) {
        usuarioRepository.save(new Usuario(new NombreCompleto("Nombre", null, "Apellido", null),
                correo, passwordEncoder.encode(CONTRASENA), rol));
    }

    private ResultActions login(String correo, String contrasena) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(Map.of("correo", correo, "contrasena", contrasena));
        return mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String obtenerToken(String correo) throws Exception {
        String respuesta = login(correo, CONTRASENA).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(respuesta);
        return json.get("token").asText();
    }

    private int contarEventos(String accion, String resultado) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = ? AND resultado = ?",
                Integer.class, accion, resultado);
        return total == null ? 0 : total;
    }
}
