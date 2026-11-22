package com.inversioneslr.backend.controller;

import com.inversioneslr.backend.BaseDeDatosDePrueba;
import com.inversioneslr.backend.TestcontainersConfiguration;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsultaCalendarioDisponibilidadIntegrationTest {

    private static final String URL =
            "/api/v1/publicaciones/%d/disponibilidad";

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;

    private Usuario anfitrion;
    private Usuario otroAnfitrion;
    private Usuario huesped;

    @Autowired
    ConsultaCalendarioDisponibilidadIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate,
            UsuarioRepository usuarioRepository,
            TokenService tokenService
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.usuarioRepository = usuarioRepository;
        this.tokenService = tokenService;
    }

    @BeforeEach
    void preparar() {
        BaseDeDatosDePrueba.limpiar(jdbcTemplate);
        anfitrion = crearUsuario("anfitrion@correo.com", Rol.ANFITRION);
        otroAnfitrion = crearUsuario("otro@correo.com", Rol.ANFITRION);
        huesped = crearUsuario("huesped@correo.com", Rol.HUESPED);
    }

    @Test
    void consultar_muestraSoloLosPeriodosPropiosOrdenadosYConEstado() throws Exception {
        Long publicacionPropia = insertarPublicacion(anfitrion, "Casa propia");
        Long publicacionAjena = insertarPublicacion(otroAnfitrion, "Casa ajena");

        Long idReserva = insertarReserva(huesped, publicacionPropia);
        insertarPeriodo(publicacionPropia, null, "2026-03-01", "2026-03-03", "DISPONIBLE");
        insertarPeriodo(publicacionPropia, null, "2026-03-09", "2026-03-11", "BLOQUEADO");
        insertarPeriodo(publicacionPropia, idReserva, "2026-03-05", "2026-03-07", "RESERVADO");
        insertarPeriodo(publicacionAjena, null, "2026-03-13", "2026-03-15", "DISPONIBLE");

        mockMvc.perform(get(URL.formatted(publicacionPropia))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPublicacion").value(publicacionPropia))
                .andExpect(jsonPath("$.titulo").value("Casa propia"))
                .andExpect(jsonPath("$.periodos", hasSize(3)))
                .andExpect(jsonPath("$.periodos[*].tipo",
                        contains("DISPONIBLE", "RESERVADO", "BLOQUEADO")))
                .andExpect(jsonPath("$.periodos[1].idReserva").doesNotExist())
                .andExpect(jsonPath("$.periodos[1].fechaInicio").value("2026-03-05"));
    }

    @Test
    void consultar_publicacionAjena_responde403() throws Exception {
        Long publicacionAjena = insertarPublicacion(otroAnfitrion, "Casa ajena");

        mockMvc.perform(get(URL.formatted(publicacionAjena))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultar_sinToken_responde401() throws Exception {
        mockMvc.perform(get(URL.formatted(1)))
                .andExpect(status().isUnauthorized());
    }

    private Long insertarPublicacion(Usuario propietario, String titulo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO publicacion (id_anfitrion, id_ciudad, id_tipo, id_politica, titulo,
                                         descripcion, direccion_textual, capacidad, precio_noche,
                                         tarifa_limpieza, reglas, estado)
                VALUES (?, (SELECT id_ciudad FROM ciudad WHERE nombre = 'Cartagena'),
                        (SELECT id_tipo FROM tipo_alojamiento WHERE nombre = 'Apartamento'),
                        (SELECT id_politica FROM politica_cancelacion WHERE nombre = 'Flexible'),
                        ?, 'Descripción de prueba', 'Calle 5 # 3-20', 4, 250000, 0,
                        'Reglas de prueba', 'ACTIVA')
                RETURNING id_publicacion
                """, Long.class, propietario.getIdUsuario(), titulo);
    }

    private Long insertarReserva(Usuario huesped, Long idPublicacion) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO reserva (
                    id_huesped, id_publicacion, fecha_llegada, fecha_salida, estado,
                    clave_idempotencia, precio_base, porcentaje_descuento_aplicado,
                    valor_descuento, tarifa_limpieza, porcentaje_tarifa_servicio,
                    tarifa_servicio, total, horas_anticipacion_aplicada,
                    porcentaje_devolucion_aplicado
                )
                VALUES (?, ?, DATE '2026-03-05', DATE '2026-03-07', 'CONFIRMADA',
                        'clave-prueba', 100000, 0, 0, 0, 0, 0, 100000, 0, 0)
                RETURNING id_reserva
                """, Long.class, huesped.getIdUsuario(), idPublicacion);
    }

    private void insertarPeriodo(
            Long idPublicacion,
            Long idReserva,
            String inicio,
            String fin,
            String tipo
    ) {
        jdbcTemplate.update("""
                INSERT INTO periodo_disponibilidad
                    (id_publicacion, id_reserva, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, ?, ?)
                """,
                idPublicacion,
                idReserva,
                Date.valueOf(LocalDate.parse(inicio)),
                Date.valueOf(LocalDate.parse(fin)),
                tipo);
    }

    private Usuario crearUsuario(String correo, Rol rol) {
        return usuarioRepository.save(new Usuario(
                new NombreCompleto("Nombre", null, "Apellido", null),
                correo,
                "hash-no-usado",
                rol));
    }

    private String token(Usuario usuario) {
        return "Bearer " + tokenService.generar(usuario).token();
    }
}