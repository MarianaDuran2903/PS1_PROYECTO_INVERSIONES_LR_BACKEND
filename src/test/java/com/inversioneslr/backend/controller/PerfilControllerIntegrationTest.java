package com.inversioneslr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PerfilControllerIntegrationTest {

    private static final String URL = "/api/v1/usuarios/me";
    private static final String CONTRASENA = "Segura#123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private EventoAuditoriaRepository eventoAuditoriaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void limpiar() {
        eventoAuditoriaRepository.deleteAllInBatch();
        usuarioRepository.deleteAllInBatch();
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void consultarPerfil_cualquierRol_devuelveSoloSuCuenta(Rol rol)
            throws Exception {
        Usuario propio = crearUsuario("propio@example.com", rol);
        crearUsuario("ajeno@example.com", Rol.HUESPED);
        String token = obtenerToken(propio.getCorreo());

        var resultado = mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario")
                        .value(propio.getIdUsuario().intValue()))
                .andExpect(jsonPath("$.correo").value("propio@example.com"))
                .andExpect(jsonPath("$.rol").value(rol.name()))
                .andExpect(jsonPath("$.primerNombre").value("Ana"))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        var respuesta = objectMapper.readTree(
                resultado.getResponse().getContentAsString());

        assertThat(respuesta.isObject()).isTrue();
        assertThat(respuesta.size()).isEqualTo(8);
        assertThat(respuesta.toString()).doesNotContain("ajeno@example.com");
    }

    @Test
    void consultarPerfil_sinToken_responde401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesión"));
    }

    @Test
    void consultarPerfil_tokenInvalido_responde401() throws Exception {
        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cuenta_activa", "restringido"})
    void consultarPerfil_cuentaDejaDeEstarHabilitada_rechazaTokenPrevio(
            String campo) throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        if ("cuenta_activa".equals(campo)) {
            jdbcTemplate.update(
                    "UPDATE usuario SET cuenta_activa = false WHERE id_usuario = ?",
                    usuario.getIdUsuario());
        } else {
            jdbcTemplate.update(
                    "UPDATE usuario SET restringido = true WHERE id_usuario = ?",
                    usuario.getIdUsuario());
        }

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void consultarPerfil_reflejaDatosActualizadosEnBase() throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        jdbcTemplate.update(
                "UPDATE usuario SET primer_nombre = ? WHERE id_usuario = ?",
                "Actualizada", usuario.getIdUsuario());

        mockMvc.perform(get(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primerNombre").value("Actualizada"));
    }

    @Test
    void contratoOpenApi_documentaConsultaDelPerfil() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].get.responses['200']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].get.responses['401']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].get.security",
                        notNullValue()));
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.saveAndFlush(new Usuario(
                new NombreCompleto("Ana", null, "Ruiz", null),
                correo,
                passwordEncoder.encode(CONTRASENA),
                rol));
    }

    private String obtenerToken(String correo) throws Exception {
        String cuerpo = objectMapper.writeValueAsString(Map.of(
                "correo", correo,
                "contrasena", CONTRASENA));

        var resultado = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                        resultado.getResponse().getContentAsString())
                .get("token")
                .asText();
    }
}