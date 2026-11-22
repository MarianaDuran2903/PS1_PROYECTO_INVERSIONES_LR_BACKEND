package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.ReservaProperties;
import com.inversioneslr.backend.dto.request.CrearReservaRequest;
import com.inversioneslr.backend.dto.response.ReservaResponse;
import com.inversioneslr.backend.exception.AlojamientoNoDisponibleException;
import com.inversioneslr.backend.exception.ClaveIdempotenciaReutilizadaException;
import com.inversioneslr.backend.exception.DatosReservaInvalidosException;
import com.inversioneslr.backend.exception.FechasNoDisponiblesException;
import com.inversioneslr.backend.mapper.ReservaMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.ConfiguracionPlataforma;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.DesglosePrecio;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.EstadoReserva;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import com.inversioneslr.backend.repository.ConfiguracionPlataformaRepository;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.ExpiracionReservasService;
import com.inversioneslr.backend.service.ReservaService.ResultadoCreacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaServiceImplTest {

    /** 10:00 en Bogotá (UTC-5) del 1 de junio de 2030. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2030-06-01T15:00:00Z"), ZoneOffset.UTC);
    private static final UsuarioAutenticado HUESPED_AUTENTICADO =
            new UsuarioAutenticado(9L, "ana@correo.com", Rol.HUESPED, Instant.parse("2030-06-01T16:00:00Z"));
    private static final String CLAVE = "clave-1";
    private static final LocalDate LLEGADA = LocalDate.parse("2030-06-10");
    private static final LocalDate SALIDA = LocalDate.parse("2030-06-13");

    @Mock
    private PublicacionRepository publicacionRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private PeriodoDisponibilidadRepository periodoRepository;
    @Mock
    private ConfiguracionPlataformaRepository configuracionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private ExpiracionReservasService expiracionReservasService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ReservaServiceImpl servicio;
    private Usuario huesped;
    private Publicacion publicacion;
    private ConfiguracionPlataforma configuracion;

    @BeforeEach
    void setUp() {
        servicio = servicioCon(RELOJ);
        huesped = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash", Rol.HUESPED);
        ReflectionTestUtils.setField(huesped, "idUsuario", 9L);
        publicacion = publicacionActiva();
        configuracion = BeanUtils.instantiateClass(ConfiguracionPlataforma.class);
        ReflectionTestUtils.setField(configuracion, "porcentajeTarifaServicio", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(configuracion, "porcentajeDescuento", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(configuracion, "nochesMinimasDescuento", 7);
    }

    private ReservaServiceImpl servicioCon(Clock reloj) {
        return new ReservaServiceImpl(publicacionRepository, reservaRepository, periodoRepository,
                configuracionRepository, usuarioRepository, auditoriaService, expiracionReservasService,
                new ReservaMapper(), new ReservaProperties(30), reloj, new TransactionTemplate(transactionManager));
    }

    // ------------------------------------------------------------------ creación

    @Test
    @DisplayName("Crea la reserva PENDIENTE_PAGO con el precio, la política y el vencimiento")
    void crear_conCalendarioCompleto_creaLaReservaPendienteDePago() {
        PeriodoDisponibilidad libre = libre("2030-06-01", "2030-07-01");
        prepararCreacion(List.of(libre));

        ResultadoCreacion resultado = servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isTrue();
        ReservaResponse reserva = resultado.reserva();
        assertThat(reserva.idReserva()).isEqualTo(77L);
        assertThat(reserva.idPublicacion()).isEqualTo(12L);
        assertThat(reserva.estado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(reserva.fechaLlegada()).isEqualTo(LLEGADA);
        assertThat(reserva.fechaSalida()).isEqualTo(SALIDA);
        assertThat(reserva.noches()).isEqualTo(3);
        assertThat(reserva.huespedes()).isEqualTo(2);
        assertThat(reserva.fechaExpiracion()).isEqualTo(OffsetDateTime.parse("2030-06-01T15:30:00Z"));
        assertThat(reserva.precio().precioBase()).isEqualByComparingTo("750000.00");
        assertThat(reserva.precio().porcentajeDescuento()).isEqualByComparingTo("0");
        assertThat(reserva.precio().tarifaLimpieza()).isEqualByComparingTo("50000.00");
        assertThat(reserva.precio().tarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(reserva.precio().total()).isEqualByComparingTo("875000.00");
        assertThat(reserva.politicaCancelacion().horasAnticipacionMin()).isEqualTo(24);
        assertThat(reserva.politicaCancelacion().porcentajeDevolucion()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Guarda al Huésped del token y la clave de idempotencia, y registra la auditoría")
    void crear_guardaLaReservaDelHuespedDelTokenYAudita() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-07-01")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getHuesped()).isSameAs(huesped);
        assertThat(guardada.getValue().getPublicacion()).isSameAs(publicacion);
        assertThat(guardada.getValue().getClaveIdempotencia()).isEqualTo(CLAVE);
        verify(auditoriaService).registrarExito(huesped, AccionAuditoria.CREAR_RESERVA, "Reserva", "77");
    }

    @Test
    @DisplayName("Una estadía que llega al mínimo de noches recibe el descuento configurado")
    void crear_conEstadiaLarga_aplicaElDescuento() {
        LocalDate salida = LocalDate.parse("2030-06-17");
        sinReservaPrevia();
        publicacionBloqueada();
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, salida))
                .thenReturn(List.of(libre("2030-06-01", "2030-07-01")));
        configuracionYHuesped();
        guardadoConIdentificador();

        ReservaResponse reserva = servicio.crear(solicitud(LLEGADA, salida, 2), CLAVE, HUESPED_AUTENTICADO)
                .reserva();

        assertThat(reserva.noches()).isEqualTo(7);
        assertThat(reserva.precio().precioBase()).isEqualByComparingTo("1750000.00");
        assertThat(reserva.precio().porcentajeDescuento()).isEqualByComparingTo("10.00");
        assertThat(reserva.precio().valorDescuento()).isEqualByComparingTo("175000.00");
        assertThat(reserva.precio().tarifaServicio()).isEqualByComparingTo("157500.00");
        assertThat(reserva.precio().total()).isEqualByComparingTo("1782500.00");
    }

    @Test
    @DisplayName("La clave de idempotencia se usa sin los espacios de los extremos")
    void crear_recortaLaClaveDeIdempotencia() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-07-01")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), "  " + CLAVE + "  ", HUESPED_AUTENTICADO);

        ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getClaveIdempotencia()).isEqualTo(CLAVE);
    }

    @Test
    @DisplayName("Antes de mirar el calendario vence las reservas pendientes ya caducadas de la publicación")
    void crear_vencePrimeroLasReservasCaducadasDeLaPublicacion() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-07-01")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        InOrder orden = inOrder(publicacionRepository, expiracionReservasService, periodoRepository);
        orden.verify(publicacionRepository).bloquearPorIdYEstado(12L, EstadoPublicacion.ACTIVA);
        orden.verify(expiracionReservasService).vencerPendientesDe(12L);
        orden.verify(periodoRepository).buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, SALIDA);
    }

    // ------------------------------------------------------------------ calendario

    @Test
    @DisplayName("Un tramo que coincide con la estadía se reemplaza solo por el tramo reservado")
    void crear_tramoExacto_dejaSoloElTramoReservado() {
        PeriodoDisponibilidad libre = libre("2030-06-10", "2030-06-13");
        prepararCreacion(List.of(libre));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        verify(periodoRepository).deleteAll(List.of(libre));
        assertThat(periodosGuardados()).containsExactly(
                tramo(TipoPeriodo.RESERVADO, "2030-06-10", "2030-06-13"));
    }

    @Test
    @DisplayName("Si sobran noches a ambos lados, quedan como tramos disponibles")
    void crear_conSobrantesAAmbosLados_losConservaDisponibles() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-07-01")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(periodosGuardados()).containsExactly(
                tramo(TipoPeriodo.DISPONIBLE, "2030-06-01", "2030-06-10"),
                tramo(TipoPeriodo.RESERVADO, "2030-06-10", "2030-06-13"),
                tramo(TipoPeriodo.DISPONIBLE, "2030-06-13", "2030-07-01"));
    }

    @Test
    @DisplayName("Si la estadía empieza donde empieza el tramo, solo sobra el final")
    void crear_conSobranteSoloDespues_conservaElFinal() {
        prepararCreacion(List.of(libre("2030-06-10", "2030-07-01")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(periodosGuardados()).containsExactly(
                tramo(TipoPeriodo.RESERVADO, "2030-06-10", "2030-06-13"),
                tramo(TipoPeriodo.DISPONIBLE, "2030-06-13", "2030-07-01"));
    }

    @Test
    @DisplayName("Si la estadía termina donde termina el tramo, solo sobra el comienzo")
    void crear_conSobranteSoloAntes_conservaElComienzo() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-06-13")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(periodosGuardados()).containsExactly(
                tramo(TipoPeriodo.DISPONIBLE, "2030-06-01", "2030-06-10"),
                tramo(TipoPeriodo.RESERVADO, "2030-06-10", "2030-06-13"));
    }

    @Test
    @DisplayName("Una estadía que cruza dos tramos contiguos los reemplaza a ambos")
    void crear_cruzandoDosTramosContiguos_losReemplazaAAmbos() {
        PeriodoDisponibilidad primero = libre("2030-06-05", "2030-06-11");
        PeriodoDisponibilidad segundo = libre("2030-06-11", "2030-06-20");
        prepararCreacion(List.of(primero, segundo));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        verify(periodoRepository).deleteAll(List.of(primero, segundo));
        assertThat(periodosGuardados()).containsExactly(
                tramo(TipoPeriodo.DISPONIBLE, "2030-06-05", "2030-06-10"),
                tramo(TipoPeriodo.RESERVADO, "2030-06-10", "2030-06-13"),
                tramo(TipoPeriodo.DISPONIBLE, "2030-06-13", "2030-06-20"));
    }

    @Test
    @DisplayName("La verificación de superposición se pospone antes de reescribir el calendario")
    void crear_posponeLaVerificacionAntesDeReescribirElCalendario() {
        PeriodoDisponibilidad libre = libre("2030-06-01", "2030-07-01");
        prepararCreacion(List.of(libre));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        InOrder orden = inOrder(reservaRepository, periodoRepository);
        orden.verify(reservaRepository).saveAndFlush(any(Reserva.class));
        orden.verify(periodoRepository).diferirVerificacionDeSolape();
        orden.verify(periodoRepository).deleteAll(List.of(libre));
        orden.verify(periodoRepository, times(3)).save(any(PeriodoDisponibilidad.class));
    }

    @Test
    @DisplayName("El tramo reservado queda asociado a la reserva creada")
    void crear_elTramoReservadoPerteneceALaReserva() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-07-01")));

        servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        ArgumentCaptor<PeriodoDisponibilidad> guardados = ArgumentCaptor.forClass(PeriodoDisponibilidad.class);
        verify(periodoRepository, times(3)).save(guardados.capture());
        PeriodoDisponibilidad reservado = guardados.getAllValues().stream()
                .filter(periodo -> periodo.getTipo() == TipoPeriodo.RESERVADO).findFirst().orElseThrow();
        assertThat(reservado.getReserva().getIdReserva()).isEqualTo(77L);
        assertThat(reservado.getPublicacion()).isSameAs(publicacion);
    }

    // ------------------------------------------------------------------ idempotencia

    @Test
    @DisplayName("La misma clave con la misma solicitud devuelve la reserva original sin crear otra")
    void crear_conClaveYaUsadaYMismaSolicitud_devuelveLaOriginal() {
        Reserva previa = reservaPrevia(LLEGADA, SALIDA, 2);
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE))
                .thenReturn(Optional.of(previa));

        ResultadoCreacion resultado = servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isFalse();
        assertThat(resultado.reserva().idReserva()).isEqualTo(55L);
        verify(reservaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(publicacionRepository, periodoRepository, expiracionReservasService);
        verify(auditoriaService, never()).registrarExito(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("La repetición devuelve la reserva aunque la llegada ya sea pasada o la publicación cambie")
    void crear_conClaveYaUsada_noRevalidaLasFechas() {
        Reserva previa = reservaPrevia(LocalDate.parse("2030-05-20"), LocalDate.parse("2030-05-22"), 2);
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE))
                .thenReturn(Optional.of(previa));

        ResultadoCreacion resultado = servicio.crear(
                solicitud(LocalDate.parse("2030-05-20"), LocalDate.parse("2030-05-22"), 2), CLAVE,
                HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isFalse();
        assertThat(resultado.reserva().idReserva()).isEqualTo(55L);
    }

    @Test
    @DisplayName("Si la misma solicitud ya se completó mientras esperaba el bloqueo, devuelve esa reserva")
    void crear_conReservaCreadaMientrasEsperabaElBloqueo_devuelveLaOriginal() {
        Reserva creadaPorLaOtra = reservaPrevia(LLEGADA, SALIDA, 2);
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE))
                .thenReturn(Optional.empty(), Optional.of(creadaPorLaOtra));
        publicacionBloqueada();

        ResultadoCreacion resultado = servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isFalse();
        assertThat(resultado.reserva().idReserva()).isEqualTo(55L);
        verify(reservaRepository, never()).saveAndFlush(any());
        verifyNoInteractions(periodoRepository, expiracionReservasService);
    }

    @Test
    @DisplayName("La misma clave con otra solicitud se rechaza")
    void crear_conClaveYaUsadaYOtraSolicitud_seRechaza() {
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE))
                .thenReturn(Optional.of(reservaPrevia(LLEGADA, SALIDA, 2)));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA.plusDays(1), 2), CLAVE,
                HUESPED_AUTENTICADO))
                .isInstanceOf(ClaveIdempotenciaReutilizadaException.class);

        verify(auditoriaService).registrarFallo("ana@correo.com", AccionAuditoria.CREAR_RESERVA, "Reserva",
                "CLAVE_IDEMPOTENCIA_REUTILIZADA");
        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Si dos solicitudes con la misma clave compiten, la perdedora recibe la reserva de la ganadora")
    void crear_conCarreraPorLaMismaClave_devuelveLaReservaGanadora() {
        Reserva ganadora = reservaPrevia(LLEGADA, SALIDA, 2);
        // Antes del bloqueo y después de él todavía no existe; recién tras el error aparece la de la ganadora.
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(ganadora));
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(violacion("uq_reserva_idempotencia"));

        ResultadoCreacion resultado = servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isFalse();
        assertThat(resultado.reserva().idReserva()).isEqualTo(55L);
    }

    @Test
    @DisplayName("Si la clave que ganó la carrera era de otra solicitud, se rechaza")
    void crear_conCarreraPorLaMismaClaveYOtraSolicitud_seRechaza() {
        Reserva ganadora = reservaPrevia(LLEGADA, SALIDA.plusDays(2), 2);
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(ganadora));
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(violacion("uq_reserva_idempotencia"));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(ClaveIdempotenciaReutilizadaException.class);
    }

    @Test
    @DisplayName("Si la clave repetida ya no se encuentra, se propaga el error original")
    void crear_conClaveRepetidaQueDesaparece_propagaElError() {
        sinReservaPrevia();
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        DataIntegrityViolationException error = violacion("uq_reserva_idempotencia");
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(error);

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isSameAs(error);
    }

    // ------------------------------------------------------------------ validaciones

    @Test
    @DisplayName("La llegada anterior a hoy se rechaza antes de consultar la publicación")
    void crear_conLlegadaPasada_seRechaza() {
        sinReservaPrevia();

        assertThatThrownBy(() -> servicio.crear(solicitud(LocalDate.parse("2030-05-31"), LLEGADA, 2), CLAVE,
                HUESPED_AUTENTICADO))
                .isInstanceOfSatisfying(DatosReservaInvalidosException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("fechaLlegada");
                    assertThat(error.getMessage()).contains("anterior a hoy");
                });

        verifyNoInteractions(publicacionRepository);
        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Llegar hoy se permite")
    void crear_conLlegadaHoy_sePermite() {
        LocalDate hoy = LocalDate.parse("2030-06-01");
        sinReservaPrevia();
        when(publicacionRepository.bloquearPorIdYEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, hoy, hoy.plusDays(2)))
                .thenReturn(List.of(libre("2030-06-01", "2030-07-01")));
        configuracionYHuesped();
        guardadoConIdentificador();

        ResultadoCreacion resultado = servicio.crear(solicitud(hoy, hoy.plusDays(2), 2), CLAVE,
                HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isTrue();
    }

    @Test
    @DisplayName("Hoy se cuenta en la zona del negocio: a las 10 p. m. en Bogotá aún es el mismo día aunque en UTC ya no")
    void crear_conLlegadaDeHoyEnBogotaPeroAyerEnUtc_sePermite() {
        // 2030-06-02T03:00Z es el 1 de junio a las 10 p. m. en Bogotá.
        ReservaServiceImpl nocturno = servicioCon(Clock.fixed(Instant.parse("2030-06-02T03:00:00Z"),
                ZoneOffset.UTC));
        LocalDate hoyEnBogota = LocalDate.parse("2030-06-01");
        sinReservaPrevia();
        when(publicacionRepository.bloquearPorIdYEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, hoyEnBogota, hoyEnBogota.plusDays(1)))
                .thenReturn(List.of(libre("2030-06-01", "2030-07-01")));
        configuracionYHuesped();
        guardadoConIdentificador();

        ResultadoCreacion resultado = nocturno.crear(solicitud(hoyEnBogota, hoyEnBogota.plusDays(1), 2), CLAVE,
                HUESPED_AUTENTICADO);

        assertThat(resultado.creada()).isTrue();
    }

    @Test
    @DisplayName("Una publicación inexistente o que no está ACTIVA responde que no está disponible")
    void crear_conPublicacionNoActiva_seRechaza() {
        sinReservaPrevia();
        when(publicacionRepository.bloquearPorIdYEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(AlojamientoNoDisponibleException.class);

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Más huéspedes que la capacidad se rechaza indicando el máximo")
    void crear_conMasHuespedesQueLaCapacidad_seRechaza() {
        sinReservaPrevia();
        when(publicacionRepository.bloquearPorIdYEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 5), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOfSatisfying(DatosReservaInvalidosException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("huespedes");
                    assertThat(error.getMessage()).contains("4");
                });

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Una cantidad de huéspedes igual a la capacidad se permite")
    void crear_conHuespedesIgualesALaCapacidad_sePermite() {
        prepararCreacion(List.of(libre("2030-06-01", "2030-07-01")));

        ResultadoCreacion resultado = servicio.crear(solicitud(LLEGADA, SALIDA, 4), CLAVE, HUESPED_AUTENTICADO);

        assertThat(resultado.reserva().huespedes()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ fechas no disponibles

    @Test
    @DisplayName("Sin ningún tramo disponible las fechas se rechazan")
    void crear_sinTramosDisponibles_seRechaza() {
        sinReservaPrevia();
        publicacionBloqueada();
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, SALIDA)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(FechasNoDisponiblesException.class);

        verify(auditoriaService).registrarFallo("ana@correo.com", AccionAuditoria.CREAR_RESERVA, "Reserva",
                "FECHAS_NO_DISPONIBLES");
        verify(reservaRepository, never()).saveAndFlush(any());
        verify(periodoRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("Un tramo que cubre solo parte de las noches se rechaza")
    void crear_conCalendarioParcial_seRechaza() {
        sinReservaPrevia();
        publicacionBloqueada();
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, SALIDA))
                .thenReturn(List.of(libre("2030-06-10", "2030-06-12")));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(FechasNoDisponiblesException.class);

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Dos tramos con un hueco entre ellos no cubren la estadía")
    void crear_conUnHuecoEntreTramos_seRechaza() {
        sinReservaPrevia();
        publicacionBloqueada();
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, SALIDA))
                .thenReturn(List.of(libre("2030-06-09", "2030-06-11"), libre("2030-06-12", "2030-06-20")));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(FechasNoDisponiblesException.class);
    }

    @Test
    @DisplayName("Si la base detecta la doble reserva al guardar, responde que las fechas no están disponibles")
    void crear_conExclusionDeReservaViolada_seRechazaPorFechas() {
        sinReservaPrevia();
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(violacion("ex_reserva_sin_solape"));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(FechasNoDisponiblesException.class);

        verify(auditoriaService).registrarFallo("ana@correo.com", AccionAuditoria.CREAR_RESERVA, "Reserva",
                "FECHAS_NO_DISPONIBLES");
        verify(auditoriaService, never()).registrarExito(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Si la base detecta calendarios superpuestos al terminar, también responde fechas no disponibles")
    void crear_conExclusionDeCalendarioViolada_seRechazaPorFechas() {
        sinReservaPrevia();
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(violacion("ex_periodo_sin_solape"));

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(FechasNoDisponiblesException.class);
    }

    @Test
    @DisplayName("Una violación de integridad desconocida no se disfraza: se propaga")
    void crear_conOtraViolacionDeIntegridad_sePropaga() {
        sinReservaPrevia();
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        DataIntegrityViolationException error = violacion("ck_reserva_montos");
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(error);

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isSameAs(error);
    }

    @Test
    @DisplayName("Una violación sin mensaje en la causa no rompe el análisis del error")
    void crear_conViolacionSinMensaje_sePropaga() {
        sinReservaPrevia();
        prepararCreacionHastaGuardar(List.of(libre("2030-06-01", "2030-07-01")));
        DataIntegrityViolationException error = new DataIntegrityViolationException(null);
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenThrow(error);

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isSameAs(error);
    }

    @Test
    @DisplayName("Sin la configuración de la plataforma no se puede calcular el precio")
    void crear_sinConfiguracionDeLaPlataforma_falla() {
        sinReservaPrevia();
        publicacionBloqueada();
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, SALIDA))
                .thenReturn(List.of(libre("2030-06-01", "2030-07-01")));
        when(configuracionRepository.findById(ConfiguracionPlataforma.ID_UNICO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.crear(solicitud(LLEGADA, SALIDA, 2), CLAVE, HUESPED_AUTENTICADO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configuración");

        verify(reservaRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------ apoyo

    private static CrearReservaRequest solicitud(LocalDate llegada, LocalDate salida, int huespedes) {
        return new CrearReservaRequest(12L, llegada, salida, huespedes);
    }

    private void sinReservaPrevia() {
        when(reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(9L, CLAVE)).thenReturn(Optional.empty());
    }

    private void publicacionBloqueada() {
        when(publicacionRepository.bloquearPorIdYEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));
    }

    /** Todo lo que la creación consulta y guarda, para la estadía del 10 al 13 de junio. */
    private void prepararCreacion(List<PeriodoDisponibilidad> libres) {
        sinReservaPrevia();
        prepararCreacionHastaGuardar(libres);
        guardadoConIdentificador();
    }

    /** Lo que se consulta antes de guardar la reserva, sin simular el guardado. */
    private void prepararCreacionHastaGuardar(List<PeriodoDisponibilidad> libres) {
        publicacionBloqueada();
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, LLEGADA, SALIDA)).thenReturn(libres);
        configuracionYHuesped();
    }

    private void configuracionYHuesped() {
        when(configuracionRepository.findById(ConfiguracionPlataforma.ID_UNICO))
                .thenReturn(Optional.of(configuracion));
        when(usuarioRepository.getReferenceById(9L)).thenReturn(huesped);
    }

    /** El guardado asigna el identificador 77, como lo haría la base. */
    private void guardadoConIdentificador() {
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(invocacion -> {
            Reserva reserva = invocacion.getArgument(0);
            ReflectionTestUtils.setField(reserva, "idReserva", 77L);
            return reserva;
        });
    }

    private Publicacion publicacionActiva() {
        Usuario anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null), "carlos@correo.com",
                "hash", Rol.ANFITRION);
        PoliticaCancelacion flexible = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(flexible, "nombre", "Flexible");
        ReflectionTestUtils.setField(flexible, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(flexible, "porcentajeDevolucion", new BigDecimal("100.00"));
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "Bocagrande", 4, new BigDecimal("250000.00"));
        Publicacion creada = new Publicacion(anfitrion, flexible, datos, new Ciudad("Cartagena"),
                new TipoAlojamiento("Apartamento"));
        ReflectionTestUtils.setField(creada, "idPublicacion", 12L);
        ReflectionTestUtils.setField(creada, "estado", EstadoPublicacion.ACTIVA);
        ReflectionTestUtils.setField(creada, "tarifaLimpieza", new BigDecimal("50000.00"));
        return creada;
    }

    private Reserva reservaPrevia(LocalDate llegada, LocalDate salida, int huespedes) {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"),
                ChronoUnit.DAYS.between(llegada, salida), BigDecimal.ZERO, new BigDecimal("10.00"));
        Reserva previa = Reserva.preReservar(huesped, publicacion, llegada, salida, huespedes, CLAVE, precio,
                publicacion.getPolitica(), OffsetDateTime.parse("2030-06-01T15:30:00Z"));
        ReflectionTestUtils.setField(previa, "idReserva", 55L);
        return previa;
    }

    private PeriodoDisponibilidad libre(String inicio, String fin) {
        return PeriodoDisponibilidad.disponible(publicacion, LocalDate.parse(inicio), LocalDate.parse(fin));
    }

    private List<Tramo> periodosGuardados() {
        ArgumentCaptor<PeriodoDisponibilidad> guardados = ArgumentCaptor.forClass(PeriodoDisponibilidad.class);
        verify(periodoRepository, atLeastOnce()).save(guardados.capture());
        return guardados.getAllValues().stream()
                .map(periodo -> new Tramo(periodo.getTipo(), periodo.getFechaInicio(), periodo.getFechaFin()))
                .toList();
    }

    private static Tramo tramo(TipoPeriodo tipo, String inicio, String fin) {
        return new Tramo(tipo, LocalDate.parse(inicio), LocalDate.parse(fin));
    }

    private static DataIntegrityViolationException violacion(String restriccion) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: violates constraint \"" + restriccion + "\""));
    }

    private record Tramo(TipoPeriodo tipo, LocalDate inicio, LocalDate fin) {
    }
}
