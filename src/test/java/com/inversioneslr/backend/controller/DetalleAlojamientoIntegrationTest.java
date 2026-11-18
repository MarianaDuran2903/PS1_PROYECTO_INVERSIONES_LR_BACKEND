package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DetalleAlojamientoIntegrationTest {

    private static final String URL = "/api/v1/alojamientos/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Test
    void detalle_sinToken_muestraInformacionPublica() throws Exception {
        long id = insertarPublicacion("ACTIVA");

        mockMvc.perform(get(URL + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(id))
                .andExpect(jsonPath("$.titulo").value("Casa de prueba"))
                .andExpect(jsonPath("$.descripcion").value("Descripción real"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.ciudad.nombre").value("Cartagena"))
                .andExpect(jsonPath("$.barrioZona").value("Bocagrande"))
                .andExpect(jsonPath("$.tipo.nombre").value("Apartamento"))
                .andExpect(jsonPath("$.capacidad").value(4))
                .andExpect(jsonPath("$.precioNoche").value(250000))
                .andExpect(jsonPath("$.reglas").value("No fumar"))
                .andExpect(jsonPath("$.politica.nombre").value("Flexible"))
                .andExpect(jsonPath("$.politica.horasAnticipacionMin").isNumber())
                .andExpect(jsonPath("$.politica.porcentajeDevolucion").isNumber())
                .andExpect(jsonPath("$.servicios", hasSize(0)))
                .andExpect(jsonPath("$.imagenes", hasSize(0)))
                .andExpect(jsonPath("$.direccionTextual").doesNotExist())
                .andExpect(jsonPath("$.idAnfitrion").doesNotExist())
                .andExpect(jsonPath("$.motivoBloqueo").doesNotExist());
    }

    @Test
    void detalle_muestraSoloServiciosAsociadosEImagenesOrdenadas()
            throws Exception {
        long id = insertarPublicacion("ACTIVA");
        long otra = insertarPublicacion("ACTIVA");

        long cocina = servicio("Cocina");
        long wifi = servicio("Wi-Fi");
        long parqueadero = servicio("Parqueadero");

        asociarServicio(id, wifi);
        asociarServicio(id, cocina);
        asociarServicio(otra, parqueadero);

        jdbcTemplate.update("""
                INSERT INTO imagen_publicacion (id_publicacion, orden, ruta)
                VALUES (?, 2, '/referencias/segunda.jpg'),
                       (?, 1, '/referencias/primera.jpg'),
                       (?, 1, '/referencias/otra-publicacion.jpg')
                """, id, id, otra);

        mockMvc.perform(get(URL + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicios", hasSize(2)))
                .andExpect(jsonPath("$.servicios[0].id").value(cocina))
                .andExpect(jsonPath("$.servicios[0].nombre").value("Cocina"))
                .andExpect(jsonPath("$.servicios[1].id").value(wifi))
                .andExpect(jsonPath("$.servicios[1].nombre").value("Wi-Fi"))
                .andExpect(jsonPath("$.imagenes", hasSize(2)))
                .andExpect(jsonPath("$.imagenes[0].orden").value(1))
                .andExpect(jsonPath("$.imagenes[0].ruta")
                        .value("/referencias/primera.jpg"))
                .andExpect(jsonPath("$.imagenes[1].orden").value(2))
                .andExpect(jsonPath("$.imagenes[1].ruta")
                        .value("/referencias/segunda.jpg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "BORRADOR",
            "PENDIENTE_REVISION",
            "PAUSADA",
            "BLOQUEADA"
    })
    void detalle_noActiva_respondeNoDisponible(String estado)
            throws Exception {
        long id = insertarPublicacion(estado);

        mockMvc.perform(get(URL + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje")
                        .value("El alojamiento no está disponible"))
                .andExpect(jsonPath("$.titulo").doesNotExist());
    }

    @Test
    void detalle_inexistente_respondeNoDisponible() throws Exception {
        mockMvc.perform(get(URL + Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje")
                        .value("El alojamiento no está disponible"));
    }

    @Test
    void detalle_alDejarDeEstarActiva_dejaDeMostrarse() throws Exception {
        long id = insertarPublicacion("ACTIVA");

        mockMvc.perform(get(URL + id))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                UPDATE publicacion
                SET estado = 'PAUSADA'
                WHERE id_publicacion = ?
                """, id);

        mockMvc.perform(get(URL + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje")
                        .value("El alojamiento no está disponible"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc"})
    void detalle_identificadorInvalido_responde400(String id)
            throws Exception {
        mockMvc.perform(get(URL + id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idPublicacion").exists());
    }

    @Test
    void detalle_tokenInvalido_noImpideConsultaPublica() throws Exception {
        long id = insertarPublicacion("ACTIVA");

        mockMvc.perform(get(URL + id)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer token-invalido"))
                .andExpect(status().isOk());
    }

    @Test
    void detalle_permisoPublico_noAbreEscrituras() throws Exception {
        mockMvc.perform(post(URL + "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void detalle_openApi_documentaConsultaPublica() throws Exception {
        String operacion =
                "$.paths['/api/v1/alojamientos/{idPublicacion}'].get";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(operacion + ".responses['200']").exists())
                .andExpect(jsonPath(operacion + ".responses['400']").exists())
                .andExpect(jsonPath(operacion + ".responses['404']").exists())
                .andExpect(jsonPath(operacion + ".security").isArray())
                .andExpect(jsonPath(operacion + ".security").isEmpty());
    }

    private long insertarPublicacion(String estado) {
        Usuario anfitrion = usuarioRepository.save(new Usuario(
                new NombreCompleto("Nombre", null, "Apellido", null),
                "detalle-" + UUID.randomUUID() + "@correo.com",
                "hash-no-usado",
                Rol.ANFITRION));

        return jdbcTemplate.queryForObject("""
                INSERT INTO publicacion (
                    id_anfitrion, id_ciudad, id_tipo, id_politica,
                    titulo, descripcion, direccion_textual, barrio_zona,
                    capacidad, precio_noche, tarifa_limpieza, reglas, estado
                )
                VALUES (
                    ?,
                    (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                    (SELECT id_tipo FROM tipo_alojamiento
                     WHERE nombre = 'Apartamento'),
                    (SELECT id_politica FROM politica_cancelacion
                     WHERE nombre = 'Flexible'),
                    'Casa de prueba', 'Descripción real', 'Dirección privada',
                    'Bocagrande', 4, 250000, 0, 'No fumar', ?
                )
                RETURNING id_publicacion
                """, Long.class, anfitrion.getIdUsuario(), estado);
    }

    private long servicio(String nombre) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO servicio (nombre)
                VALUES (?)
                ON CONFLICT (nombre)
                DO UPDATE SET nombre = EXCLUDED.nombre
                RETURNING id_servicio
                """, Long.class, nombre);
    }

    private void asociarServicio(long publicacion, long servicio) {
        jdbcTemplate.update("""
                INSERT INTO publicacion_servicio
                    (id_publicacion, id_servicio)
                VALUES (?, ?)
                """, publicacion, servicio);
    }
}