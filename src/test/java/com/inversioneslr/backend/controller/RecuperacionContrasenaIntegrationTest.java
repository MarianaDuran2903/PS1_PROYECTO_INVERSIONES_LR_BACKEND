package com.inversioneslr.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.EventoAuditoriaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.GeneradorTokenRecuperacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recuperación de contraseña simulada de punta a punta, con PostgreSQL real (Testcontainers) y la migración V5.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecuperacionContrasenaIntegrationTest {

    private static final String URL_SOLICITUD = "/api/v1/auth/recuperacion/solicitud";
    private static final String URL_RESTABLECER = "/api/v1/auth/recuperacion/restablecer";
    private static final String URL_LOGIN = "/api/v1/auth/login";
    private static final String CORREO = "ana@correo.com";
    private static final String CONTRASENA_ANTERIOR = "Segura#123";
    private static final String CONTRASENA_NUEVA = "Nueva#456";
    private static final String TOKEN = "token-de-prueba-0123456789-abcdefghijklmnop";
    private static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, recibirás las instrucciones para restablecer tu contraseña";
    private static final String MENSAJE_TOKEN_INVALIDO = "El enlace de recuperación no es válido o ha expirado";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioRepository usuarioRepository;
    private final EventoAuditoriaRepository eventoAuditoriaRepository;

    @SpyBean
    private GeneradorTokenRecuperacion generadorToken;

    @Autowired
    RecuperacionContrasenaIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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
    void preparar() {
        eventoAuditoriaRepository.deleteAllInBatch();
        // Las solicitudes de recuperación se borran en cascada con el usuario.
        usuarioRepository.deleteAllInBatch();
        usuarioRepository.save(new Usuario(new NombreCompleto("Ana", null, "Pérez", null), CORREO,
                passwordEncoder.encode(CONTRASENA_ANTERIOR), Rol.HUESPED));
    }

    @Test
    @DisplayName("Criterios 1, 3 y 4: solicita, establece una nueva contraseña e inicia sesión con ella")
    void flujoCompleto_permiteIniciarSesionConLaNuevaContrasena() throws Exception {
        doReturn(TOKEN).when(generadorToken).generar();

        solicitar(CORREO)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_SOLICITUD));
        restablecer(TOKEN, CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Tu contraseña se actualizó. Ya puedes iniciar sesión con la nueva contraseña"));

        login(CONTRASENA_NUEVA).andExpect(status().isOk()).andExpect(jsonPath("$.token", notNullValue()));
        login(CONTRASENA_ANTERIOR).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Criterio 1: la solicitud queda registrada con el token cifrado y vencimiento a 30 minutos")
    void solicitar_correoRegistrado_guardaSolicitudConTokenCifrado() throws Exception {
        doReturn(TOKEN).when(generadorToken).generar();

        solicitar("ANA@Correo.com").andExpect(status().isAccepted());

        Map<String, Object> fila = jdbcTemplate.queryForMap("""
                SELECT s.token_hash, s.fecha_uso, u.correo,
                       extract(epoch FROM s.fecha_expiracion - s.fecha_solicitud) AS segundos
                  FROM solicitud_recuperacion s JOIN usuario u ON u.id_usuario = s.id_usuario
                """);
        assertThat(fila).containsEntry("correo", CORREO).containsEntry("fecha_uso", null);
        assertThat((String) fila.get("token_hash")).hasSize(64).isNotEqualTo(TOKEN)
                .isEqualTo(generadorToken.hash(TOKEN));
        assertThat(((Number) fila.get("segundos")).intValue()).isEqualTo(1800);
    }

    @Test
    @DisplayName("Criterio 2: un correo no registrado recibe exactamente la misma respuesta y no se genera token")
    void solicitar_correoNoRegistrado_respuestaIdenticaSinSolicitud() throws Exception {
        String registrado = solicitar(CORREO).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        jdbcTemplate.update("DELETE FROM solicitud_recuperacion");

        String noRegistrado = solicitar("nadie@correo.com").andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(noRegistrado).isEqualTo(registrado);
        assertThat(contar("SELECT count(*) FROM solicitud_recuperacion")).isZero();
        assertThat(contar("""
                SELECT count(*) FROM evento_auditoria
                 WHERE accion = 'SOLICITUD_RECUPERACION' AND resultado = 'FALLIDO' AND id_usuario IS NULL
                """)).isEqualTo(1);
    }

    @Test
    void solicitar_cuentaInactiva_respondeIgualSinSolicitud() throws Exception {
        jdbcTemplate.update("UPDATE usuario SET cuenta_activa = false");

        solicitar(CORREO)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_SOLICITUD));

        assertThat(contar("SELECT count(*) FROM solicitud_recuperacion")).isZero();
    }

    @Test
    void solicitar_correoInvalido_responde400EnElCampo() throws Exception {
        solicitar("no-es-un-correo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.correo").value("El correo no tiene un formato válido"));
    }

    @Test
    @DisplayName("Criterio 3: la nueva contraseña debe cumplir las reglas y coincidir con la confirmación")
    void restablecer_contrasenaInvalida_responde400YElTokenSigueVigente() throws Exception {
        doReturn(TOKEN).when(generadorToken).generar();
        solicitar(CORREO).andExpect(status().isAccepted());

        restablecer(TOKEN, "debil", "debil")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasena", notNullValue()));
        restablecer(TOKEN, CONTRASENA_NUEVA, "Otra#789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.confirmarContrasena")
                        .value("La confirmación no coincide con la contraseña"));

        restablecer(TOKEN, CONTRASENA_NUEVA, CONTRASENA_NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("El token es de un solo uso")
    void restablecer_tokenYaUsado_responde400() throws Exception {
        doReturn(TOKEN).when(generadorToken).generar();
        solicitar(CORREO).andExpect(status().isAccepted());
        restablecer(TOKEN, CONTRASENA_NUEVA, CONTRASENA_NUEVA).andExpect(status().isOk());

        restablecer(TOKEN, "Tercera#789", "Tercera#789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));

        login(CONTRASENA_NUEVA).andExpect(status().isOk());
    }

    @Test
    void restablecer_tokenVencido_responde400SinCambiarLaContrasena() throws Exception {
        doReturn(TOKEN).when(generadorToken).generar();
        solicitar(CORREO).andExpect(status().isAccepted());
        jdbcTemplate.update("UPDATE solicitud_recuperacion SET fecha_solicitud = now() - interval '2 hours', "
                + "fecha_expiracion = now() - interval '1 hour'");

        restablecer(TOKEN, CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));

        login(CONTRASENA_ANTERIOR).andExpect(status().isOk());
    }

    @Test
    void restablecer_tokenInexistente_responde400() throws Exception {
        restablecer("token-que-nunca-se-emitio", CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_TOKEN_INVALIDO));
    }

    @Test
    @DisplayName("Una solicitud nueva deja sin efecto el enlace anterior")
    void solicitar_dosVeces_soloFuncionaElUltimoToken() throws Exception {
        String primero = TOKEN + "-1";
        String segundo = TOKEN + "-2";
        doReturn(primero, segundo).when(generadorToken).generar();
        solicitar(CORREO).andExpect(status().isAccepted());
        solicitar(CORREO).andExpect(status().isAccepted());

        restablecer(primero, CONTRASENA_NUEVA, CONTRASENA_NUEVA).andExpect(status().isBadRequest());
        restablecer(segundo, CONTRASENA_NUEVA, CONTRASENA_NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Criterio 5: la solicitud y el cambio de contraseña quedan registrados")
    void flujoCompleto_registraEventosDeAuditoria() throws Exception {
        doReturn(TOKEN).when(generadorToken).generar();
        solicitar(CORREO).andExpect(status().isAccepted());
        restablecer(TOKEN, CONTRASENA_NUEVA, CONTRASENA_NUEVA).andExpect(status().isOk());

        List<Map<String, Object>> eventos = jdbcTemplate.queryForList("""
                SELECT e.accion, e.resultado, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion IN ('SOLICITUD_RECUPERACION', 'RECUPERACION_CONTRASENA')
                 ORDER BY e.id_evento
                """);
        assertThat(eventos).extracting(e -> e.get("accion"))
                .containsExactly("SOLICITUD_RECUPERACION", "RECUPERACION_CONTRASENA");
        assertThat(eventos).allSatisfy(e -> {
            assertThat(e).containsEntry("resultado", "EXITOSO");
            assertThat(e).containsEntry("correo", CORREO);
        });
    }

    @Test
    void restablecer_tokenInvalido_registraIntentoFallido() throws Exception {
        restablecer("token-que-nunca-se-emitio", CONTRASENA_NUEVA, CONTRASENA_NUEVA)
                .andExpect(status().isBadRequest());

        assertThat(contar("""
                SELECT count(*) FROM evento_auditoria
                 WHERE accion = 'RECUPERACION_CONTRASENA' AND resultado = 'FALLIDO'
                """)).isEqualTo(1);
    }

    @Test
    void contratoOpenApi_documentaRecuperacion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + URL_SOLICITUD + "'].post.responses['202']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + URL_RESTABLECER + "'].post.responses['200']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + URL_RESTABLECER + "'].post.responses['400']", notNullValue()));
    }

    private ResultActions solicitar(String correo) throws Exception {
        return mockMvc.perform(post(URL_SOLICITUD).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("correo", correo))));
    }

    private ResultActions restablecer(String token, String contrasena, String confirmacion) throws Exception {
        return mockMvc.perform(post(URL_RESTABLECER).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token, "contrasena", contrasena,
                        "confirmarContrasena", confirmacion))));
    }

    private ResultActions login(String contrasena) throws Exception {
        return mockMvc.perform(post(URL_LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("correo", CORREO, "contrasena", contrasena))));
    }

    private int contar(String consulta) {
        Integer total = jdbcTemplate.queryForObject(consulta, Integer.class);
        return total == null ? 0 : total;
    }
}
