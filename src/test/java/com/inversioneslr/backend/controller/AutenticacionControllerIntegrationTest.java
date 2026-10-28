package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.TestcontainersConfiguration;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AutenticacionControllerIntegrationTest {

    private static final String URL_REGISTRO = "/api/v1/auth/registro/huesped";

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
    @DisplayName("Dado un visitante con datos válidos, cuando completa el registro, entonces se crea la cuenta con rol Huésped")
    void registrar_datosValidos_creaCuentaHuesped() throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana Pérez", "Ana.Perez@Correo.com", "Segura123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idUsuario", notNullValue()))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.correo").value("ana.perez@correo.com"))
                .andExpect(jsonPath("$.rol").value("HUESPED"))
                .andExpect(jsonPath("$.cuentaActiva").value(true))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist());

        Map<String, Object> fila = jdbcTemplate.queryForMap(
                "SELECT rol, contrasena_hash, cuenta_activa, restringido FROM usuario WHERE correo = ?",
                "ana.perez@correo.com");
        assertThat(fila.get("rol")).isEqualTo("HUESPED");
        assertThat(fila.get("cuenta_activa")).isEqualTo(true);
        assertThat(fila.get("restringido")).isEqualTo(false);
        String hash = (String) fila.get("contrasena_hash");
        assertThat(hash).isNotEqualTo("Segura123").startsWith("$2");
        assertThat(passwordEncoder.matches("Segura123", hash)).isTrue();

        Integer eventos = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'REGISTRO_USUARIO' AND resultado = 'EXITOSO'",
                Integer.class);
        assertThat(eventos).isEqualTo(1);
    }

    @Test
    @DisplayName("El visitante no puede elegir su rol: un rol enviado en el cuerpo se ignora")
    void registrar_conRolEnElCuerpo_siempreQuedaHuesped() throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Eva","correo":"eva@correo.com","contrasena":"Segura123","rol":"ADMINISTRADOR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("HUESPED"));
    }

    @Test
    @DisplayName("Una contraseña de 12 caracteres es válida (límite superior)")
    void registrar_contrasenaDe12Caracteres_esValida() throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Luis", "luis@correo.com", "Abcdefgh1234")))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "nombre vacío          | ''        | ana@correo.com | Segura123     | nombre",
            "correo inválido       | Ana       | no-es-correo   | Segura123     | correo",
            "contraseña de 7       | Ana       | ana@correo.com | Corta12       | contrasena",
            "contraseña de 13      | Ana       | ana@correo.com | Muylarga12345 | contrasena"
    })
    @DisplayName("Datos inválidos responden 400 con el campo que falló")
    void registrar_datosInvalidos_responde400(String caso, String nombre, String correo, String contrasena,
                                              String campo) throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(nombre, correo, contrasena)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));

        assertThat(usuarioRepository.count()).isZero();
    }

    @Test
    @DisplayName("Campos ausentes responden 400")
    void registrar_camposAusentes_responde400() throws Exception {
        mockMvc.perform(post(URL_REGISTRO).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nombre", notNullValue()))
                .andExpect(jsonPath("$.errores.correo", notNullValue()))
                .andExpect(jsonPath("$.errores.contrasena", notNullValue()));
    }

    @Test
    @DisplayName("Un JSON mal formado responde 400")
    void registrar_jsonMalFormado_responde400() throws Exception {
        mockMvc.perform(post(URL_REGISTRO).contentType(MediaType.APPLICATION_JSON).content("{nombre:"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("JSON")));
    }

    @Test
    @DisplayName("Un correo ya registrado (aunque cambien las mayúsculas) responde 409")
    void registrar_correoDuplicado_responde409() throws Exception {
        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Ana", "ana@correo.com", "Segura123")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL_REGISTRO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("Otra Ana", "ANA@correo.com", "Segura456")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andExpect(jsonPath("$.mensaje").value("El correo ya está registrado"));

        assertThat(usuarioRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Las rutas no públicas exigen autenticación (401)")
    void rutaProtegida_sinAutenticacion_responde401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El contrato OpenAPI es público y documenta el endpoint de registro")
    void contratoOpenApi_documentaRegistro() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + URL_REGISTRO + "'].post.responses['201']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + URL_REGISTRO + "'].post.responses['409']", notNullValue()));
    }

    private static String json(String nombre, String correo, String contrasena) {
        return """
                {"nombre":"%s","correo":"%s","contrasena":"%s"}
                """.formatted(nombre, correo, contrasena);
    }
}
