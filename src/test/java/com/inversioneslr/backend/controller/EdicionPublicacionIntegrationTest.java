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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta y edición de publicaciones por su Anfitrión propietario. PostgreSQL real con las migraciones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EdicionPublicacionIntegrationTest {

    private static final String URL = "/api/v1/publicaciones";
    private static final String TITULO_ORIGINAL = "Apartamento con vista al mar";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private long idCartagena;
    private long idMedellin;
    private long idApartamento;
    private long idCasa;
    private long idPublicacion;

    @Autowired
    EdicionPublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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
        idCartagena = idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'");
        idMedellin = idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Medellín'");
        idApartamento = idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'");
        idCasa = idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Casa'");
        idPublicacion = crearPublicacion(anfitrion);
    }

    @Test
    @DisplayName("Criterio 1: el propietario guarda los cambios válidos")
    void actualizar_propietario_guardaCambios() throws Exception {
        actualizar(anfitrion, idPublicacion, cuerpoEdicion())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(idPublicacion))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.titulo").value("Casa campestre renovada"))
                .andExpect(jsonPath("$.ciudad.nombre").value("Medellín"))
                .andExpect(jsonPath("$.tipo.nombre").value("Casa"))
                .andExpect(jsonPath("$.barrioZona").doesNotExist())
                .andExpect(jsonPath("$.capacidad").value(6))
                .andExpect(jsonPath("$.precioNoche").value(320000.00))
                .andExpect(jsonPath("$.tarifaLimpieza").value(40000.00))
                .andExpect(jsonPath("$.reglas").value("No se admiten mascotas."))
                .andExpect(jsonPath("$.politica.nombre").value("Flexible"));

        Map<String, Object> fila = filaPublicacion();
        assertThat(fila)
                .containsEntry("titulo", "Casa campestre renovada")
                .containsEntry("id_ciudad", idMedellin)
                .containsEntry("capacidad", 6)
                .containsEntry("reglas", "No se admiten mascotas.")
                .containsEntry("estado", "BORRADOR");
        assertThat((BigDecimal) fila.get("tarifa_limpieza")).isEqualByComparingTo("40000.00");
    }

    @Test
    @DisplayName("Criterio 1: la edición queda registrada para trazabilidad")
    void actualizar_registraEventoDeAuditoria() throws Exception {
        actualizar(anfitrion, idPublicacion, cuerpoEdicion()).andExpect(status().isOk());

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'EDITAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(idPublicacion))
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("correo", "anfitrion@correo.com");
    }

    @Test
    @DisplayName("Criterio 1: el cuerpo no puede cambiar el propietario ni el estado")
    void actualizar_ignoraAnfitrionYEstadoDelCuerpo() throws Exception {
        Usuario otro = crearUsuario("otro.anfitrion@correo.com", Rol.ANFITRION);
        Map<String, Object> cuerpo = cuerpoEdicion();
        cuerpo.put("idAnfitrion", otro.getIdUsuario());
        cuerpo.put("estado", "ACTIVA");

        actualizar(anfitrion, idPublicacion, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idAnfitrion").value(anfitrion.getIdUsuario()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        assertThat(filaPublicacion()).containsEntry("id_anfitrion", anfitrion.getIdUsuario());
    }

    @Test
    void actualizar_publicacionPausada_seEditaYSigueEnPausada() throws Exception {
        cambiarEstado("PAUSADA");

        actualizar(anfitrion, idPublicacion, cuerpoEdicion())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PAUSADA"));

        assertThat(filaPublicacion()).containsEntry("titulo", "Casa campestre renovada");
    }

    @Test
    @DisplayName("Criterio 2: editar una publicación de otro Anfitrión se deniega y se registra el intento")
    void actualizar_publicacionAjena_responde403SinCambios() throws Exception {
        Usuario intruso = crearUsuario("intruso@correo.com", Rol.ANFITRION);

        actualizar(intruso, idPublicacion, cuerpoEdicion())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("No tiene permisos para esta operación"));

        assertThat(filaPublicacion()).containsEntry("titulo", TITULO_ORIGINAL);
        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'ACCESO_DENEGADO'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", String.valueOf(idPublicacion))
                .containsEntry("resultado", "FALLIDO")
                .containsEntry("correo", "intruso@correo.com");
    }

    @Test
    @DisplayName("Criterio 3: con datos inválidos se informan los errores y se conserva lo registrado")
    void actualizar_datosInvalidos_responde400YConservaLaInformacion() throws Exception {
        Map<String, Object> cuerpo = cuerpoEdicion();
        cuerpo.put("titulo", "  ");
        cuerpo.put("capacidad", 0);
        cuerpo.put("precioNoche", new BigDecimal("-1"));
        cuerpo.put("tarifaLimpieza", new BigDecimal("-5"));
        cuerpo.put("reglas", "r".repeat(2001));

        actualizar(anfitrion, idPublicacion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.titulo", notNullValue()))
                .andExpect(jsonPath("$.errores.capacidad", notNullValue()))
                .andExpect(jsonPath("$.errores.precioNoche", notNullValue()))
                .andExpect(jsonPath("$.errores.tarifaLimpieza").value("La tarifa de limpieza no puede ser negativa"))
                .andExpect(jsonPath("$.errores.reglas").value("Las reglas admiten máximo 2000 caracteres"));

        assertPublicacionSinCambios();
    }

    @Test
    @DisplayName("Criterio 3: con una ciudad inexistente se conserva lo registrado")
    void actualizar_ciudadInexistente_responde400YConservaLaInformacion() throws Exception {
        Map<String, Object> cuerpo = cuerpoEdicion();
        cuerpo.put("idCiudad", 99999);

        actualizar(anfitrion, idPublicacion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idCiudad").value("La ciudad no existe"));

        assertPublicacionSinCambios();
    }

    @Test
    void actualizar_sinTarifaLimpieza_responde400() throws Exception {
        Map<String, Object> cuerpo = cuerpoEdicion();
        cuerpo.remove("tarifaLimpieza");

        actualizar(anfitrion, idPublicacion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.tarifaLimpieza").value("La tarifa de limpieza es obligatoria"));
    }

    @Test
    @DisplayName("Criterio 4: una publicación activa no se edita: hay que pausarla primero")
    void actualizar_publicacionActiva_responde409SinCambios() throws Exception {
        cambiarEstado("ACTIVA");

        actualizar(anfitrion, idPublicacion, cuerpoEdicion())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La publicación está activa: solicite pausarla para editarla"));

        assertThat(filaPublicacion())
                .containsEntry("titulo", TITULO_ORIGINAL)
                .containsEntry("estado", "ACTIVA");
    }

    @ParameterizedTest(name = "estado {0}")
    @ValueSource(strings = {"PENDIENTE_REVISION", "BLOQUEADA"})
    void actualizar_estadoNoEditable_responde409SinCambios(String estado) throws Exception {
        cambiarEstado(estado);

        actualizar(anfitrion, idPublicacion, cuerpoEdicion()).andExpect(status().isConflict());

        assertThat(filaPublicacion())
                .containsEntry("titulo", TITULO_ORIGINAL)
                .containsEntry("estado", estado);
    }

    @Test
    void actualizar_publicacionInexistente_responde404() throws Exception {
        actualizar(anfitrion, idPublicacion + 1000, cuerpoEdicion())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La publicación no existe"));
    }

    @Test
    void actualizar_identificadorNoNumerico_responde400() throws Exception {
        mockMvc.perform(put(URL + "/abc")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoEdicion())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idPublicacion").value("El tipo de dato no es válido"));
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ADMINISTRADOR"})
    void actualizar_otroRol_responde403SinCambios(String rol) throws Exception {
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@correo.com", Rol.valueOf(rol));

        actualizar(usuario, idPublicacion, cuerpoEdicion()).andExpect(status().isForbidden());

        assertThat(filaPublicacion()).containsEntry("titulo", TITULO_ORIGINAL);
    }

    @Test
    void actualizar_sinToken_responde401() throws Exception {
        mockMvc.perform(put(URL + "/" + idPublicacion).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoEdicion())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Consultar: el propietario obtiene su publicación para precargar el formulario")
    void obtener_propietario_devuelveLaPublicacion() throws Exception {
        mockMvc.perform(get(URL + "/" + idPublicacion).header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(idPublicacion))
                .andExpect(jsonPath("$.titulo").value(TITULO_ORIGINAL))
                .andExpect(jsonPath("$.ciudad.id").value(idCartagena))
                .andExpect(jsonPath("$.tipo.id").value(idApartamento))
                .andExpect(jsonPath("$.reglas").doesNotExist());
    }

    @Test
    void obtener_publicacionAjena_responde403() throws Exception {
        Usuario intruso = crearUsuario("intruso@correo.com", Rol.ANFITRION);

        mockMvc.perform(get(URL + "/" + idPublicacion).header(HttpHeaders.AUTHORIZATION, token(intruso)))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_publicacionInexistente_responde404() throws Exception {
        mockMvc.perform(get(URL + "/" + (idPublicacion + 1000)).header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isNotFound());
    }

    @Test
    void contratoOpenApi_documentaConsultarYEditar() throws Exception {
        String ruta = "$.paths['" + URL + "/{idPublicacion}']";
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ruta + ".get.responses['200']", notNullValue()))
                .andExpect(jsonPath(ruta + ".get.responses['404']", notNullValue()))
                .andExpect(jsonPath(ruta + ".put.responses['200']", notNullValue()))
                .andExpect(jsonPath(ruta + ".put.responses['403']", notNullValue()))
                .andExpect(jsonPath(ruta + ".put.responses['409']", notNullValue()));
    }

    private void assertPublicacionSinCambios() {
        Map<String, Object> fila = filaPublicacion();
        assertThat(fila)
                .containsEntry("titulo", TITULO_ORIGINAL)
                .containsEntry("id_ciudad", idCartagena)
                .containsEntry("capacidad", 4);
        assertThat((BigDecimal) fila.get("precio_noche")).isEqualByComparingTo("250000.00");
        assertThat((BigDecimal) fila.get("tarifa_limpieza")).isEqualByComparingTo("0");
        Integer ediciones = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'EDITAR_PUBLICACION'", Integer.class);
        assertThat(ediciones).isZero();
    }

    private Map<String, Object> cuerpoEdicion() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", "Casa campestre renovada");
        cuerpo.put("descripcion", "Tres habitaciones, patio y chimenea.");
        cuerpo.put("direccionTextual", "Carrera 7 # 10-15");
        cuerpo.put("idCiudad", idMedellin);
        cuerpo.put("barrioZona", null);
        cuerpo.put("idTipo", idCasa);
        cuerpo.put("capacidad", 6);
        cuerpo.put("precioNoche", new BigDecimal("320000.00"));
        cuerpo.put("tarifaLimpieza", new BigDecimal("40000.00"));
        cuerpo.put("reglas", "No se admiten mascotas.");
        return cuerpo;
    }

    private long crearPublicacion(Usuario propietario) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", TITULO_ORIGINAL);
        cuerpo.put("descripcion", "Dos habitaciones, cocina equipada y balcón.");
        cuerpo.put("direccionTextual", "Calle 5 # 3-20");
        cuerpo.put("idCiudad", idCartagena);
        cuerpo.put("barrioZona", "Bocagrande");
        cuerpo.put("idTipo", idApartamento);
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

    /**
     * Ningún endpoint cambia todavía el estado; se ajusta directamente en la base. Las reglas se llenan porque
     * la base las exige para PENDIENTE_REVISION y ACTIVA.
     */
    private void cambiarEstado(String estado) {
        jdbcTemplate.update("UPDATE publicacion SET reglas = 'Reglas de prueba', estado = ? WHERE id_publicacion = ?",
                estado, idPublicacion);
    }

    private ResultActions actualizar(Usuario usuario, long id, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(put(URL + "/" + id)
                .header(HttpHeaders.AUTHORIZATION, token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private Map<String, Object> filaPublicacion() {
        return jdbcTemplate.queryForMap("""
                SELECT titulo, id_ciudad, id_anfitrion, capacidad, precio_noche, tarifa_limpieza, reglas, estado
                  FROM publicacion WHERE id_publicacion = ?
                """, idPublicacion);
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
