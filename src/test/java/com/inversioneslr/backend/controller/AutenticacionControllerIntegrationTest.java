package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AutenticacionControllerIntegrationTest {

    private static final String URL_HUESPED = "/api/v1/auth/registro/huesped";
    private static final String URL_ANFITRION = "/api/v1/auth/registro/anfitrion";
    private static final String CONTRASENA = "Segura#123";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;
    private final EventoAuditoriaRepository eventoAuditoriaRepository;

    @Autowired
    AutenticacionControllerIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate,
                                           PasswordEncoder passwordEncoder,
                                           UsuarioRepository usuarioRepository,
                                           EventoAuditoriaRepository eventoAuditoriaRepository) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
        this.eventoAuditoriaRepository = eventoAuditoriaRepository;
    }

    @BeforeEach
    void limpiarDatos() {
        eventoAuditoriaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("HU-001: dado un visitante con datos válidos, cuando se registra como Huésped, entonces se crea la cuenta con rol Huésped")
    void registrarHuesped_datosValidos_creaCuentaHuesped() throws Exception {
        registrar(URL_HUESPED, json("Ana", "", "Pérez", "", "Ana.Perez@Correo.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUsuario", notNullValue()))
                .andExpect(jsonPath("$.primerNombre").value("Ana"))
                .andExpect(jsonPath("$.segundoNombre", nullValue()))
                .andExpect(jsonPath("$.primerApellido").value("Pérez"))
                .andExpect(jsonPath("$.correo").value("ana.perez@correo.com"))
                .andExpect(jsonPath("$.rol").value("HUESPED"))
                .andExpect(jsonPath("$.cuentaActiva").value(true));

        assertCuentaEnBaseDeDatos("ana.perez@correo.com", "HUESPED");
    }

    @Test
    @DisplayName("HU-002 criterio 1: dado un visitante con datos válidos, cuando se registra como Anfitrión, entonces se crea la cuenta con rol Anfitrión")
    void registrarAnfitrion_datosValidos_creaCuentaAnfitrion() throws Exception {
        registrar(URL_ANFITRION, json("Carlos", "Andrés", "Gómez", "O'Neill", "carlos@correo.com"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUsuario", notNullValue()))
                .andExpect(jsonPath("$.primerNombre").value("Carlos"))
                .andExpect(jsonPath("$.segundoNombre").value("Andrés"))
                .andExpect(jsonPath("$.primerApellido").value("Gómez"))
                .andExpect(jsonPath("$.segundoApellido").value("O'Neill"))
                .andExpect(jsonPath("$.rol").value("ANFITRION"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.confirmarContrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());

        assertCuentaEnBaseDeDatos("carlos@correo.com", "ANFITRION");
    }

    @Test
    @DisplayName("HU-002 criterio 4: dado un registro exitoso, entonces queda el evento de registro para trazabilidad")
    void registrarAnfitrion_exitoso_registraEventoDeAuditoria() throws Exception {
        registrar(URL_ANFITRION, json("Carlos", "", "Gómez", "", "carlos@correo.com"))
                .andExpect(status().isCreated());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.accion, e.tipo_entidad, e.resultado, e.id_entidad, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                """);
        assertThat(evento)
                .containsEntry("accion", "REGISTRO_USUARIO")
                .containsEntry("tipo_entidad", "Usuario")
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("correo", "carlos@correo.com");
        assertThat(evento.get("id_entidad")).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {URL_HUESPED, URL_ANFITRION})
    @DisplayName("El visitante no puede elegir su rol: un rol enviado en el cuerpo se ignora")
    void registrar_conRolEnElCuerpo_seIgnora(String url) throws Exception {
        String cuerpo = json("Eva", "", "Ruiz", "", "eva@correo.com")
                .replace("}", ",\"rol\":\"ADMINISTRADOR\"}");

        registrar(url, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value(url.equals(URL_HUESPED) ? "HUESPED" : "ANFITRION"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {URL_HUESPED, URL_ANFITRION})
    @DisplayName("HU-002 criterio 2: dado un correo ya existente, entonces informa que está registrado y no crea una cuenta duplicada")
    void registrar_correoDuplicado_responde409(String url) throws Exception {
        registrar(URL_HUESPED, json("Ana", "", "Pérez", "", "ana@correo.com"))
                .andExpect(status().isCreated());

        registrar(url, json("Otra", "", "Ana", "", "ANA@correo.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.mensaje").value("El correo ya está registrado"));

        assertThat(usuarioRepository.count()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "primer nombre con números      | Ana2 | Pérez  | ana@correo.com | Segura#123    | Segura#123 | primerNombre",
            "primer apellido vacío          | Ana  | ''     | ana@correo.com | Segura#123    | Segura#123 | primerApellido",
            "correo inválido                | Ana  | Pérez  | no-es-correo   | Segura#123    | Segura#123 | correo",
            "contraseña de 7                | Ana  | Pérez  | ana@correo.com | Segu#12       | Segu#12    | contrasena",
            "contraseña sin mayúscula       | Ana  | Pérez  | ana@correo.com | segura#123    | segura#123 | contrasena",
            "contraseña sin especial        | Ana  | Pérez  | ana@correo.com | Segura1234    | Segura1234 | contrasena",
            "confirmación distinta          | Ana  | Pérez  | ana@correo.com | Segura#123    | Otra#12345 | confirmarContrasena"
    })
    @DisplayName("HU-002 criterio 3: datos inválidos muestran el error específico y no crean la cuenta")
    void registrarAnfitrion_datosInvalidos_responde400(String caso, String primerNombre, String primerApellido,
                                                       String correo, String contrasena, String confirmacion,
                                                       String campo) throws Exception {
        String cuerpo = """
                {"primerNombre":"%s","primerApellido":"%s","correo":"%s","contrasena":"%s","confirmarContrasena":"%s"}
                """.formatted(primerNombre, primerApellido, correo, contrasena, confirmacion);

        registrar(URL_ANFITRION, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));

        assertThat(usuarioRepository.count()).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {URL_HUESPED, URL_ANFITRION})
    @DisplayName("HU-002 criterio 3: datos incompletos muestran un error por cada campo obligatorio")
    void registrar_camposAusentes_responde400(String url) throws Exception {
        registrar(url, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.primerNombre", notNullValue()))
                .andExpect(jsonPath("$.errores.primerApellido", notNullValue()))
                .andExpect(jsonPath("$.errores.correo", notNullValue()))
                .andExpect(jsonPath("$.errores.contrasena", notNullValue()))
                .andExpect(jsonPath("$.errores.confirmarContrasena", notNullValue()))
                .andExpect(jsonPath("$.errores.segundoNombre").doesNotExist());

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    @DisplayName("Un JSON mal formado responde 400")
    void registrar_jsonMalFormado_responde400() throws Exception {
        registrar(URL_ANFITRION, "{primerNombre:")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("JSON")));
    }

    @Test
    @DisplayName("Las rutas no públicas exigen autenticación (401)")
    void rutaProtegida_sinAutenticacion_responde401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {URL_HUESPED, URL_ANFITRION})
    @DisplayName("El contrato OpenAPI es público y documenta los endpoints de registro")
    void contratoOpenApi_documentaRegistro(String url) throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + url + "'].post.responses['201']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + url + "'].post.responses['400']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + url + "'].post.responses['409']", notNullValue()));
    }

    private ResultActions registrar(String url, String cuerpo) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private void assertCuentaEnBaseDeDatos(String correo, String rolEsperado) {
        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT rol, contrasena_hash, cuenta_activa, restringido FROM usuario WHERE correo = ?", correo);
        assertThat(fila)
                .containsEntry("rol", rolEsperado)
                .containsEntry("cuenta_activa", true)
                .containsEntry("restringido", false);
        String hash = (String) fila.get("contrasena_hash");
        assertThat(hash).isNotEqualTo(CONTRASENA).startsWith("$2");
        assertThat(passwordEncoder.matches(CONTRASENA, hash)).isTrue();
    }

    private static String json(String primerNombre, String segundoNombre, String primerApellido,
                               String segundoApellido, String correo) {
        return """
                {"primerNombre":"%s","segundoNombre":"%s","primerApellido":"%s","segundoApellido":"%s",\
                "correo":"%s","contrasena":"%s","confirmarContrasena":"%s"}""".formatted(
                primerNombre, segundoNombre, primerApellido, segundoApellido, correo, CONTRASENA, CONTRASENA);
    }
}
