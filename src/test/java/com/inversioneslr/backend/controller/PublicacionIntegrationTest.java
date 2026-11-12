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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Creación de publicaciones de alojamiento. PostgreSQL real con las migraciones y catálogos de V2.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicacionIntegrationTest {

    private static final String URL = "/api/v1/publicaciones";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private long idCartagena;
    private long idApartamento;

    @Autowired
    PublicacionIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper, JdbcTemplate jdbcTemplate,
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
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        idCartagena = idCatalogo("SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'");
        idApartamento = idCatalogo("SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'");
    }

    @Test
    @DisplayName("Criterio 1: con datos válidos la publicación se guarda en BORRADOR")
    void crear_datosValidos_guardaEnBorrador() throws Exception {
        crear(anfitrion, cuerpoValido())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idPublicacion", notNullValue()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.titulo").value("Apartamento con vista al mar"))
                .andExpect(jsonPath("$.ciudad.nombre").value("Cartagena"))
                .andExpect(jsonPath("$.tipo.nombre").value("Apartamento"))
                .andExpect(jsonPath("$.politica.nombre").value("Flexible"))
                .andExpect(jsonPath("$.capacidad").value(4))
                .andExpect(jsonPath("$.precioNoche").value(250000.00))
                .andExpect(jsonPath("$.tarifaLimpieza").value(0));

        Map<String, Object> fila = jdbcTemplate.queryForMap("SELECT estado, capacidad, precio_noche FROM publicacion");
        assertThat(fila)
                .containsEntry("estado", "BORRADOR")
                .containsEntry("capacidad", 4);
        assertThat((BigDecimal) fila.get("precio_noche")).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("Criterio 2: el sistema solicita título, descripción, ubicación, tipo, capacidad y precio")
    void crear_sinDatos_solicitaLaInformacionMinima() throws Exception {
        crear(anfitrion, Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.titulo", notNullValue()))
                .andExpect(jsonPath("$.errores.descripcion", notNullValue()))
                .andExpect(jsonPath("$.errores.direccionTextual", notNullValue()))
                .andExpect(jsonPath("$.errores.idCiudad", notNullValue()))
                .andExpect(jsonPath("$.errores.idTipo", notNullValue()))
                .andExpect(jsonPath("$.errores.capacidad", notNullValue()))
                .andExpect(jsonPath("$.errores.precioNoche", notNullValue()))
                .andExpect(jsonPath("$.errores.barrioZona").doesNotExist());

        assertThat(contarPublicaciones()).isZero();
    }

    @ParameterizedTest(name = "capacidad = {0}")
    @ValueSource(ints = {0, -1})
    @DisplayName("Criterio 3: una capacidad no válida se informa y no se guarda la publicación")
    void crear_capacidadNoValida_responde400SinGuardar(int capacidad) throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("capacidad", capacidad);

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.capacidad").value("La capacidad debe ser un número entero mayor que 0"));

        assertThat(contarPublicaciones()).isZero();
    }

    @ParameterizedTest(name = "precioNoche = {0}")
    @ValueSource(strings = {"0", "-5000", "100.555"})
    @DisplayName("Criterio 3: un precio no positivo o con más de 2 decimales se informa y no se guarda")
    void crear_precioNoValido_responde400SinGuardar(String precio) throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("precioNoche", new BigDecimal(precio));

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precioNoche", notNullValue()));

        assertThat(contarPublicaciones()).isZero();
    }

    @Test
    @DisplayName("Criterio 3: una capacidad decimal no se trunca: se rechaza en el campo")
    void crear_capacidadDecimal_responde400EnElCampo() throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("capacidad", 2.5);

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.capacidad").value("El tipo de dato no es válido"));

        assertThat(contarPublicaciones()).isZero();
    }

    @Test
    void crear_precioNoNumerico_responde400EnElCampo() throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("precioNoche", "abc");

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.precioNoche", notNullValue()));
    }

    @Test
    void crear_descripcionDeMasDe1000Caracteres_responde400() throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("descripcion", "a".repeat(1001));

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.descripcion").value("La descripción admite máximo 1000 caracteres"));
    }

    @Test
    void crear_ciudadInexistente_responde400EnElCampo() throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("idCiudad", 99999);

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idCiudad").value("La ciudad no existe"));

        assertThat(contarPublicaciones()).isZero();
    }

    @Test
    void crear_tipoInexistente_responde400EnElCampo() throws Exception {
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("idTipo", 99999);

        crear(anfitrion, cuerpo)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.idTipo").value("El tipo de alojamiento no existe"));
    }

    @Test
    @DisplayName("Criterio 4: queda asociada al Anfitrión del token aunque el cuerpo diga otra cosa")
    void crear_ignoraAnfitrionYEstadoDelCuerpo() throws Exception {
        Usuario otro = crearUsuario("otro.anfitrion@correo.com", Rol.ANFITRION);
        Map<String, Object> cuerpo = cuerpoValido();
        cuerpo.put("idAnfitrion", otro.getIdUsuario());
        cuerpo.put("estado", "ACTIVA");

        crear(anfitrion, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idAnfitrion").value(anfitrion.getIdUsuario()))
                .andExpect(jsonPath("$.estado").value("BORRADOR"));

        Long idAnfitrionEnBd = jdbcTemplate.queryForObject("SELECT id_anfitrion FROM publicacion", Long.class);
        assertThat(idAnfitrionEnBd).isEqualTo(anfitrion.getIdUsuario());
    }

    @Test
    @DisplayName("Criterio 4: la creación queda registrada para trazabilidad")
    void crear_registraEventoDeAuditoria() throws Exception {
        String idPublicacion = objectMapper.readTree(crear(anfitrion, cuerpoValido())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("idPublicacion").asText();

        Map<String, Object> evento = jdbcTemplate.queryForMap("""
                SELECT e.tipo_entidad, e.id_entidad, e.resultado, u.correo
                  FROM evento_auditoria e JOIN usuario u ON u.id_usuario = e.id_usuario
                 WHERE e.accion = 'CREAR_PUBLICACION'
                """);
        assertThat(evento)
                .containsEntry("tipo_entidad", "Publicacion")
                .containsEntry("id_entidad", idPublicacion)
                .containsEntry("resultado", "EXITOSO")
                .containsEntry("correo", "anfitrion@correo.com");
    }

    @ParameterizedTest(name = "rol {0}")
    @ValueSource(strings = {"HUESPED", "ADMINISTRADOR"})
    @DisplayName("Solo un Anfitrión crea publicaciones: otro rol recibe 403 y se registra el intento")
    void crear_otroRol_responde403YRegistraIntento(String rol) throws Exception {
        Usuario usuario = crearUsuario(rol.toLowerCase() + "@correo.com", Rol.valueOf(rol));

        crear(usuario, cuerpoValido()).andExpect(status().isForbidden());

        assertThat(contarPublicaciones()).isZero();
        Integer denegados = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM evento_auditoria WHERE accion = 'ACCESO_DENEGADO'", Integer.class);
        assertThat(denegados).isEqualTo(1);
    }

    @Test
    void crear_sinToken_responde401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpoValido())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Los catálogos del formulario se listan ordenados por nombre")
    void catalogos_ordenadosPorNombre() throws Exception {
        String token = token(anfitrion);

        mockMvc.perform(get("/api/v1/ciudades").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Bogotá", "Cali", "Cartagena", "Medellín")));
        mockMvc.perform(get("/api/v1/tipos-alojamiento").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", contains("Apartamento", "Cabaña", "Casa", "Habitación")));
    }

    @Test
    void catalogos_sinToken_responde401() throws Exception {
        mockMvc.perform(get("/api/v1/ciudades")).andExpect(status().isUnauthorized());
    }

    @Test
    void contratoOpenApi_documentaCrearPublicacion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + URL + "'].post.responses['201']", notNullValue()))
                .andExpect(jsonPath("$.paths['" + URL + "'].post.responses['403']", notNullValue()))
                .andExpect(jsonPath("$.paths['/api/v1/ciudades'].get.responses['200']", notNullValue()));
    }

    private Map<String, Object> cuerpoValido() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("titulo", "Apartamento con vista al mar");
        cuerpo.put("descripcion", "Dos habitaciones, cocina equipada y balcón.");
        cuerpo.put("direccionTextual", "Calle 5 # 3-20");
        cuerpo.put("idCiudad", idCartagena);
        cuerpo.put("barrioZona", "Bocagrande");
        cuerpo.put("idTipo", idApartamento);
        cuerpo.put("capacidad", 4);
        cuerpo.put("precioNoche", new BigDecimal("250000.00"));
        return cuerpo;
    }

    private ResultActions crear(Usuario usuario, Map<String, Object> cuerpo) throws Exception {
        return mockMvc.perform(post(URL)
                .header(HttpHeaders.AUTHORIZATION, token(usuario))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
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

    private int contarPublicaciones() {
        Integer total = jdbcTemplate.queryForObject("SELECT count(*) FROM publicacion", Integer.class);
        return total == null ? 0 : total;
    }
}
