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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    @Test
    void abrirPeriodo_propioYValido_registraDisponibilidad() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");

        mockMvc.perform(post(URL.formatted(idPublicacion))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaInicio":"2026-06-10","fechaFin":"2026-06-13"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaInicio").value("2026-06-10"))
                .andExpect(jsonPath("$.fechaFin").value("2026-06-13"))
                .andExpect(jsonPath("$.tipo").value("DISPONIBLE"));

        assertThat(periodosDe(idPublicacion)).isEqualTo(1);
    }

    @Test
    void abrirPeriodo_fechaFinIgualOAnterior_responde400YSinGuardar() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");

        mockMvc.perform(post(URL.formatted(idPublicacion))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaInicio":"2026-06-10","fechaFin":"2026-06-10"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(periodosDe(idPublicacion)).isZero();
    }

    @Test
    void abrirPeriodo_rangoSolapado_responde409YSinAgregarPeriodo() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");
        insertarPeriodo(idPublicacion, null, "2026-06-10", "2026-06-12", "DISPONIBLE");

        mockMvc.perform(post(URL.formatted(idPublicacion))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaInicio":"2026-06-11","fechaFin":"2026-06-13"}
                                """))
                .andExpect(status().isConflict());

        assertThat(periodosDe(idPublicacion)).isEqualTo(1);
    }

    @Test
    void abrirPeriodo_publicacionBloqueada_responde409YSinGuardar() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");
        jdbcTemplate.update("UPDATE publicacion SET estado = 'BLOQUEADA', motivo_bloqueo = ? "
                + "WHERE id_publicacion = ?", "Motivo de prueba", idPublicacion);

        mockMvc.perform(post(URL.formatted(idPublicacion))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaInicio":"2026-06-10","fechaFin":"2026-06-13"}
                                """))
                .andExpect(status().isConflict());

        assertThat(periodosDe(idPublicacion)).isZero();
    }

    @Test
    void abrirPeriodo_publicacionAjena_responde403YSinGuardar() throws Exception {
        Long idPublicacion = insertarPublicacion(otroAnfitrion, "Casa ajena");

        mockMvc.perform(post(URL.formatted(idPublicacion))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fechaInicio":"2026-06-10","fechaFin":"2026-06-13"}
                                """))
                .andExpect(status().isForbidden());

        assertThat(periodosDe(idPublicacion)).isZero();
    }

    @Test
    void cerrarPeriodo_propioDisponible_loMarcaNoOfrecidoYDejaDeSerReservable() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");
        Long idPeriodo = insertarPeriodo(idPublicacion, null, "2026-06-10", "2026-06-13", "DISPONIBLE");

        mockMvc.perform(patch(URL.formatted(idPublicacion) + "/" + idPeriodo + "/no-ofrecido")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idPeriodo").value(idPeriodo))
                .andExpect(jsonPath("$.tipo").value("NO_OFRECIDO"));

        assertThat(tipoDelPeriodo(idPeriodo)).isEqualTo("NO_OFRECIDO");
        assertThat(periodosDisponiblesEn(idPublicacion)).isZero();
        mockMvc.perform(get(URL.formatted(idPublicacion))
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodos[0].tipo").value("NO_OFRECIDO"));
    }

    @Test
    void cerrarPeriodo_reservado_noAfectaLaReservaConfirmada() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");
        Long idReserva = insertarReserva(huesped, idPublicacion);
        Long idPeriodo = insertarPeriodo(idPublicacion, idReserva, "2026-03-05", "2026-03-07", "RESERVADO");

        mockMvc.perform(patch(URL.formatted(idPublicacion) + "/" + idPeriodo + "/no-ofrecido")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isConflict());

        assertThat(tipoDelPeriodo(idPeriodo)).isEqualTo("RESERVADO");
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?",
                String.class, idReserva)).isEqualTo("CONFIRMADA");
    }

    @Test
    void cerrarPeriodo_publicacionAjena_responde403() throws Exception {
        Long idPublicacion = insertarPublicacion(otroAnfitrion, "Casa ajena");
        Long idPeriodo = insertarPeriodo(idPublicacion, null, "2026-06-10", "2026-06-13", "DISPONIBLE");

        mockMvc.perform(patch(URL.formatted(idPublicacion) + "/" + idPeriodo + "/no-ofrecido")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isForbidden());
        assertThat(tipoDelPeriodo(idPeriodo)).isEqualTo("DISPONIBLE");
    }

    @Test
    void cerrarPeriodo_publicacionBloqueada_responde409() throws Exception {
        Long idPublicacion = insertarPublicacion(anfitrion, "Casa propia");
        Long idPeriodo = insertarPeriodo(idPublicacion, null, "2026-06-10", "2026-06-13", "DISPONIBLE");
        jdbcTemplate.update("UPDATE publicacion SET estado = 'BLOQUEADA', motivo_bloqueo = ? WHERE id_publicacion = ?",
                "Motivo de prueba", idPublicacion);

        mockMvc.perform(patch(URL.formatted(idPublicacion) + "/" + idPeriodo + "/no-ofrecido")
                        .header(HttpHeaders.AUTHORIZATION, token(anfitrion)))
                .andExpect(status().isConflict());
        assertThat(tipoDelPeriodo(idPeriodo)).isEqualTo("DISPONIBLE");
    }

    @Test
    void cerrarPeriodo_sinToken_responde401() throws Exception {
        mockMvc.perform(patch(URL.formatted(1) + "/1/no-ofrecido"))
                .andExpect(status().isUnauthorized());
    }

    private int periodosDisponiblesEn(Long idPublicacion) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM periodo_disponibilidad WHERE id_publicacion = ? AND tipo = 'DISPONIBLE'",
                Integer.class, idPublicacion);
    }

    private String tipoDelPeriodo(Long idPeriodo) {
        return jdbcTemplate.queryForObject("SELECT tipo FROM periodo_disponibilidad WHERE id_periodo = ?",
                String.class, idPeriodo);
    }

    private int periodosDe(Long idPublicacion) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM periodo_disponibilidad WHERE id_publicacion = ?",
                Integer.class, idPublicacion);
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

    private Long insertarPeriodo(
            Long idPublicacion,
            Long idReserva,
            String inicio,
            String fin,
            String tipo
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO periodo_disponibilidad
                    (id_publicacion, id_reserva, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id_periodo
                """,
                Long.class,
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
