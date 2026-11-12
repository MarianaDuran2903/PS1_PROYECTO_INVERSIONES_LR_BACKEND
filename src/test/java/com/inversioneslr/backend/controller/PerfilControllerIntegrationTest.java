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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PerfilControllerIntegrationTest {

    private static final String URL = "/api/v1/usuarios/me";
    private static final String URL_ESTADO = URL + "/estado";
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

    @ParameterizedTest
    @EnumSource(Rol.class)
    void actualizarPerfil_cualquierRol_actualizaSoloSuCuentaYRegistraAuditoria(
            Rol rol) throws Exception {
        Usuario propio = crearUsuario("propio@example.com", rol);
        Usuario ajeno = crearUsuario("ajeno@example.com", Rol.HUESPED);

        String hashOriginal = propio.getContrasenaHash();
        String token = obtenerToken(propio.getCorreo());

        var resultado = mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiosValidos())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario")
                        .value(propio.getIdUsuario().intValue()))
                .andExpect(jsonPath("$.primerNombre").value("Carlos"))
                .andExpect(jsonPath("$.segundoNombre").value("Andrés"))
                .andExpect(jsonPath("$.primerApellido").value("Gómez"))
                .andExpect(jsonPath("$.segundoApellido").value("Pérez"))
                .andExpect(jsonPath("$.correo").value("propio@example.com"))
                .andExpect(jsonPath("$.rol").value(rol.name()))
                .andExpect(jsonPath("$.cuentaActiva").value(true))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();

        var respuesta = objectMapper.readTree(
                resultado.getResponse().getContentAsString());

        assertThat(respuesta.size()).isEqualTo(8);

        Usuario actualizado = usuarioRepository.findById(
                propio.getIdUsuario()).orElseThrow();

        assertThat(actualizado.getNombre()).isEqualTo(
                new NombreCompleto("Carlos", "Andrés", "Gómez", "Pérez"));
        assertThat(actualizado.getCorreo()).isEqualTo("propio@example.com");
        assertThat(actualizado.getRol()).isEqualTo(rol);
        assertThat(actualizado.getContrasenaHash()).isEqualTo(hashOriginal);
        assertThat(actualizado.isCuentaActiva()).isTrue();
        assertThat(actualizado.isRestringido()).isFalse();

        Usuario otro = usuarioRepository.findById(
                ajeno.getIdUsuario()).orElseThrow();

        assertThat(otro.getNombre()).isEqualTo(
                new NombreCompleto("Ana", null, "Ruiz", null));

        Long eventos = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM evento_auditoria
            WHERE id_usuario = ?
              AND accion = ?
              AND tipo_entidad = ?
              AND id_entidad = ?
              AND resultado = ?
            """,
                Long.class,
                propio.getIdUsuario(),
                "ACTUALIZACION_PERFIL",
                "Usuario",
                String.valueOf(propio.getIdUsuario()),
                "EXITOSO");

        assertThat(eventos).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"primerNombre\":\"\",\"primerApellido\":\"Gómez\"}",
            "{\"primerNombre\":\"   \",\"primerApellido\":\"Gómez\"}",
            "{\"primerNombre\":\"Carlos\",\"primerApellido\":\"\"}",
            "{\"primerNombre\":\"Carlos\"}"
    })
    void actualizarPerfil_datosInvalidos_conservaDatosPrevios(
            String cuerpo) throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("Los datos enviados no son válidos"));

        Usuario sinCambios = usuarioRepository.findById(
                usuario.getIdUsuario()).orElseThrow();

        assertThat(sinCambios.getNombre()).isEqualTo(
                new NombreCompleto("Ana", null, "Ruiz", null));

        Long eventos = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM evento_auditoria
            WHERE id_usuario = ?
              AND accion = ?
            """,
                Long.class,
                usuario.getIdUsuario(),
                "ACTUALIZACION_PERFIL");

        assertThat(eventos).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "primerNombre",
            "segundoNombre",
            "primerApellido",
            "segundoApellido"
    })
    void actualizarPerfil_campoSuperaLongitud_rechazaSinModificar(
            String campo) throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        var cambios = new java.util.HashMap<>(cambiosValidos());
        cambios.put(campo, "A".repeat(51));

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambios)))
                .andExpect(status().isBadRequest());

        Usuario sinCambios = usuarioRepository.findById(
                usuario.getIdUsuario()).orElseThrow();

        assertThat(sinCambios.getNombre()).isEqualTo(
                new NombreCompleto("Ana", null, "Ruiz", null));
    }

    @Test
    void actualizarPerfil_normalizaEspaciosYPermiteOpcionalesVacios()
            throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "primerNombre": "  Carlos  ",
                              "segundoNombre": "   ",
                              "primerApellido": "  Gómez  ",
                              "segundoApellido": null
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primerNombre").value("Carlos"))
                .andExpect(jsonPath("$.primerApellido").value("Gómez"));

        Usuario actualizado = usuarioRepository.findById(
                usuario.getIdUsuario()).orElseThrow();

        assertThat(actualizado.getNombre()).isEqualTo(
                new NombreCompleto("Carlos", null, "Gómez", null));
    }

    @Test
    void actualizarPerfil_sinToken_responde401() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiosValidos())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesión"));
    }

    @Test
    void actualizarPerfil_tokenInvalido_responde401() throws Exception {
        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiosValidos())))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cuenta_activa", "restringido"})
    void actualizarPerfil_cuentaNoHabilitada_rechazaSinModificar(
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

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambiosValidos())))
                .andExpect(status().isUnauthorized());

        Usuario sinCambios = usuarioRepository.findById(
                usuario.getIdUsuario()).orElseThrow();

        assertThat(sinCambios.getNombre()).isEqualTo(
                new NombreCompleto("Ana", null, "Ruiz", null));
    }

    @Test
    void actualizarPerfil_idUsuarioEnCuerpo_noPermiteModificarOtraCuenta()
            throws Exception {
        Usuario propio = crearUsuario("propio@example.com", Rol.HUESPED);
        Usuario ajeno = crearUsuario("ajeno@example.com", Rol.HUESPED);
        String token = obtenerToken(propio.getCorreo());

        var cambios = new java.util.HashMap<>(cambiosValidos());
        cambios.put("idUsuario", String.valueOf(ajeno.getIdUsuario()));

        mockMvc.perform(put(URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario")
                        .value(propio.getIdUsuario().intValue()));

        Usuario propioActualizado = usuarioRepository.findById(
                propio.getIdUsuario()).orElseThrow();

        Usuario ajenoSinCambios = usuarioRepository.findById(
                ajeno.getIdUsuario()).orElseThrow();

        assertThat(propioActualizado.getNombre()).isEqualTo(
                new NombreCompleto("Carlos", "Andrés", "Gómez", "Pérez"));

        assertThat(ajenoSinCambios.getNombre()).isEqualTo(
                new NombreCompleto("Ana", null, "Ruiz", null));
    }

    @Test
    void contratoOpenApi_documentaActualizacionDelPerfil() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].put.responses['200']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].put.responses['400']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].put.responses['401']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].put.security[0]['"
                                + com.inversioneslr.backend.config.OpenApiConfig.ESQUEMA_BEARER
                                + "']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['/api/v1/usuarios/me'].put.requestBody.required")
                        .value(true));
    }

    @ParameterizedTest
    @EnumSource(Rol.class)
    void cambiarEstado_cualquierRol_desactivaSoloSuCuentaYRegistraAuditoria(Rol rol)
            throws Exception {
        Usuario propio = crearUsuario("propio@example.com", rol);
        Usuario ajeno = crearUsuario("ajeno@example.com", Rol.HUESPED);
        String token = obtenerToken(propio.getCorreo());

        mockMvc.perform(patch(URL_ESTADO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("cuentaActiva", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idUsuario")
                        .value(propio.getIdUsuario().intValue()))
                .andExpect(jsonPath("$.correo").value("propio@example.com"))
                .andExpect(jsonPath("$.rol").value(rol.name()))
                .andExpect(jsonPath("$.cuentaActiva").value(false))
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.contrasenaHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        Usuario propioDesactivado = usuarioRepository.findById(
                propio.getIdUsuario()).orElseThrow();

        Usuario ajenoSinCambios = usuarioRepository.findById(
                ajeno.getIdUsuario()).orElseThrow();

        assertThat(propioDesactivado.isCuentaActiva()).isFalse();
        assertThat(ajenoSinCambios.isCuentaActiva()).isTrue();

        Long eventos = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM evento_auditoria
        WHERE id_usuario = ?
          AND accion = ?
          AND tipo_entidad = ?
          AND id_entidad = ?
          AND resultado = ?
        """,
                Long.class,
                propio.getIdUsuario(),
                "DESACTIVACION_CUENTA",
                "Usuario",
                String.valueOf(propio.getIdUsuario()),
                "EXITOSO");

        assertThat(eventos).isEqualTo(1L);
    }

    @Test
    void cambiarEstado_cuentaActivaTrue_responde400() throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        mockMvc.perform(patch(URL_ESTADO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("cuentaActiva", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje")
                        .value("La reactivación requiere iniciar sesión con tus credenciales."));

        Usuario sinCambios = usuarioRepository.findById(
                usuario.getIdUsuario()).orElseThrow();

        assertThat(sinCambios.isCuentaActiva()).isTrue();

        Long eventos = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM evento_auditoria
        WHERE id_usuario = ?
          AND accion = ?
        """,
                Long.class,
                usuario.getIdUsuario(),
                "DESACTIVACION_CUENTA");

        assertThat(eventos).isZero();
    }

    @Test
    void cambiarEstado_sinToken_responde401() throws Exception {
        mockMvc.perform(patch(URL_ESTADO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("cuentaActiva", false))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debe iniciar sesión"));
    }

    @Test
    void cambiarEstado_tokenInvalido_responde401() throws Exception {
        mockMvc.perform(patch(URL_ESTADO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("cuentaActiva", false))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarEstado_cuentaYaDesactivada_rechazaTokenPrevio() throws Exception {
        Usuario usuario = crearUsuario("ana@example.com", Rol.HUESPED);
        String token = obtenerToken(usuario.getCorreo());

        jdbcTemplate.update(
                "UPDATE usuario SET cuenta_activa = false WHERE id_usuario = ?",
                usuario.getIdUsuario());

        mockMvc.perform(patch(URL_ESTADO)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("cuentaActiva", false))))
                .andExpect(status().isUnauthorized());

        Usuario sinCambios = usuarioRepository.findById(
                usuario.getIdUsuario()).orElseThrow();

        assertThat(sinCambios.isCuentaActiva()).isFalse();

        Long eventos = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM evento_auditoria
        WHERE id_usuario = ?
          AND accion = ?
        """,
                Long.class,
                usuario.getIdUsuario(),
                "DESACTIVACION_CUENTA");

        assertThat(eventos).isZero();
    }

    @Test
    void contratoOpenApi_documentaDesactivacionDeCuenta() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.paths['" + URL_ESTADO + "'].patch.responses['200']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['" + URL_ESTADO + "'].patch.responses['400']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['" + URL_ESTADO + "'].patch.responses['401']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['" + URL_ESTADO + "'].patch.security[0]['"
                                + com.inversioneslr.backend.config.OpenApiConfig.ESQUEMA_BEARER
                                + "']",
                        notNullValue()))
                .andExpect(jsonPath(
                        "$.paths['" + URL_ESTADO + "'].patch.requestBody.required")
                        .value(true));
    }

    private Map<String, String> cambiosValidos() {
        return Map.of(
                "primerNombre", "Carlos",
                "segundoNombre", "Andrés",
                "primerApellido", "Gómez",
                "segundoApellido", "Pérez");
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