package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.PagoProperties;
import com.inversioneslr.backend.dto.response.PagoResponse;
import com.inversioneslr.backend.exception.FirmaWebhookInvalidaException;
import com.inversioneslr.backend.exception.PagoNoEncontradoException;
import com.inversioneslr.backend.exception.PagoNoPermitidoException;
import com.inversioneslr.backend.exception.PasarelaNoDisponibleException;
import com.inversioneslr.backend.exception.ReservaAjenaException;
import com.inversioneslr.backend.exception.ReservaNoEncontradaException;
import com.inversioneslr.backend.mapper.PagoMapper;
import com.inversioneslr.backend.model.DesglosePrecio;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.Pago;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.model.enums.EstadoReserva;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.PagoRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PagoService.ResultadoInicio;
import com.inversioneslr.backend.service.PasarelaPago;
import com.inversioneslr.backend.service.PasarelaPago.EstadoSesion;
import com.inversioneslr.backend.service.PasarelaPago.EventoPasarela;
import com.inversioneslr.backend.service.PasarelaPago.SesionCreada;
import com.inversioneslr.backend.service.PasarelaPago.SolicitudSesion;
import com.inversioneslr.backend.service.ResultadoPagoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    private static final Instant INSTANTE = Instant.parse("2030-06-01T10:00:00Z");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2030-06-01T10:00:00Z");
    private static final OffsetDateTime SESION_VENCE = AHORA.plusMinutes(35);

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private PasarelaPago pasarela;
    @Mock
    private ResultadoPagoService resultadoPagoService;
    @Mock
    private AuditoriaService auditoriaService;

    private PagoServiceImpl servicio;
    private Usuario huesped;
    private Reserva reserva;
    private UsuarioAutenticado autenticado;

    @BeforeEach
    void setUp() {
        PagoProperties propiedades = new PagoProperties("COP", 35, 5, 3,
                "https://app.test/reservas/{idReserva}/pago/resultado",
                "https://app.test/reservas/{idReserva}/pago/resultado?cancelado=true",
                new PagoProperties.Stripe("sk_test_x", "whsec_x", 300));
        // Un administrador de transacciones sin comportamiento basta: solo importa que se ejecute el contenido.
        TransactionTemplate transaccion = new TransactionTemplate(mock(PlatformTransactionManager.class));
        servicio = new PagoServiceImpl(reservaRepository, pagoRepository, pasarela, resultadoPagoService,
                auditoriaService, new PagoMapper(), propiedades, Clock.fixed(INSTANTE, ZoneOffset.UTC),
                transaccion);

        huesped = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash", Rol.HUESPED);
        ReflectionTestUtils.setField(huesped, "idUsuario", 5L);
        autenticado = new UsuarioAutenticado(5L, "ana@correo.com", Rol.HUESPED, INSTANTE.plusSeconds(3600));

        PoliticaCancelacion politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(politica, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(politica, "porcentajeDevolucion", new BigDecimal("100.00"));
        Publicacion publicacion = BeanUtils.instantiateClass(Publicacion.class);
        ReflectionTestUtils.setField(publicacion, "titulo", "Apartamento frente al mar");
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"), 3,
                BigDecimal.ZERO, new BigDecimal("10.00"));
        reserva = Reserva.preReservar(huesped, publicacion, LocalDate.parse("2030-06-10"),
                LocalDate.parse("2030-06-13"), 2, "clave-1", precio, politica, AHORA.plusMinutes(25));
        ReflectionTestUtils.setField(reserva, "idReserva", 31L);
    }

    private Pago pagoAbierto(OffsetDateTime vence) {
        Pago pago = Pago.iniciar(reserva, "cs_test_1", "https://pago.test/cs_test_1", vence, "COP",
                AHORA.minusMinutes(5));
        ReflectionTestUtils.setField(pago, "idPago", 7L);
        return pago;
    }

    private void reservaPropia() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
    }

    // ------------------------------------------------------------------ iniciar

    @Test
    @DisplayName("Abre la sesión por el total de la reserva, alarga su plazo y deja la auditoría")
    void iniciar_abreLaSesionYAlargaElPlazo() {
        reservaPropia();
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());
        when(pagoRepository.countByReservaIdReserva(31L)).thenReturn(0L);
        when(pasarela.crearSesion(any())).thenReturn(
                new SesionCreada("cs_test_1", "https://pago.test/cs_test_1", SESION_VENCE));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocacion -> {
            Pago guardado = invocacion.getArgument(0);
            ReflectionTestUtils.setField(guardado, "idPago", 7L);
            return guardado;
        });

        ResultadoInicio resultado = servicio.iniciar(31L, autenticado);

        assertThat(resultado.creado()).isTrue();
        PagoResponse pago = resultado.pago();
        assertThat(pago.idPago()).isEqualTo(7L);
        assertThat(pago.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(pago.estadoReserva()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(pago.monto()).isEqualByComparingTo("875000.00");
        assertThat(pago.urlPago()).isEqualTo("https://pago.test/cs_test_1");
        assertThat(pago.fechaExpiracion()).isEqualTo(SESION_VENCE);

        ArgumentCaptor<SolicitudSesion> captor = ArgumentCaptor.forClass(SolicitudSesion.class);
        verify(pasarela).crearSesion(captor.capture());
        SolicitudSesion solicitud = captor.getValue();
        assertThat(solicitud.idReserva()).isEqualTo(31L);
        assertThat(solicitud.monto()).isEqualByComparingTo("875000.00");
        assertThat(solicitud.moneda()).isEqualTo("COP");
        assertThat(solicitud.venceEn()).isEqualTo(SESION_VENCE);
        assertThat(solicitud.urlExito()).isEqualTo("https://app.test/reservas/31/pago/resultado");
        assertThat(solicitud.urlCancelacion()).isEqualTo("https://app.test/reservas/31/pago/resultado?cancelado=true");
        assertThat(solicitud.descripcion())
                .contains("Reserva 31", "Apartamento frente al mar", "2030-06-10", "2030-06-13");

        // Plazo de la reserva: la sesión (35 min) más la gracia (5 min), mayor que el que tenía (25 min).
        assertThat(reserva.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(40));
        verify(auditoriaService).registrarExito(huesped, AccionAuditoria.INICIAR_PAGO, "Pago", "7",
                "Pago iniciado para la reserva 31");
    }

    @Test
    @DisplayName("Si ya hay una sesión abierta y vigente, devuelve la misma sin abrir otra")
    void iniciar_conSesionAbierta_laReutiliza() {
        reservaPropia();
        Pago existente = pagoAbierto(SESION_VENCE);
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.of(existente));

        ResultadoInicio resultado = servicio.iniciar(31L, autenticado);

        assertThat(resultado.creado()).isFalse();
        assertThat(resultado.pago().idPago()).isEqualTo(7L);
        assertThat(resultado.pago().urlPago()).isEqualTo("https://pago.test/cs_test_1");
        verifyNoInteractions(pasarela);
        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
        assertThat(reserva.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(25));
    }

    @Test
    @DisplayName("Una sesión rechazada que sigue abierta también se reutiliza: se reintenta en la misma")
    void iniciar_conSesionRechazadaAunAbierta_laReutiliza() {
        reservaPropia();
        Pago rechazado = pagoAbierto(SESION_VENCE);
        rechazado.rechazar("card_declined", "pi_1", AHORA);
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.of(rechazado));

        ResultadoInicio resultado = servicio.iniciar(31L, autenticado);

        assertThat(resultado.creado()).isFalse();
        assertThat(resultado.pago().estado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(resultado.pago().urlPago()).isNotNull();
        verifyNoInteractions(pasarela);
    }

    @Test
    @DisplayName("Si la sesión abierta ya venció por el reloj, se cierra y se abre una nueva")
    void iniciar_conSesionVencida_laCierraYAbreOtra() {
        reservaPropia();
        Pago vencido = pagoAbierto(AHORA.minusMinutes(1));
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.of(vencido));
        when(pagoRepository.countByReservaIdReserva(31L)).thenReturn(1L);
        when(pasarela.crearSesion(any())).thenReturn(
                new SesionCreada("cs_test_2", "https://pago.test/cs_test_2", SESION_VENCE));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        ResultadoInicio resultado = servicio.iniciar(31L, autenticado);

        assertThat(vencido.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        verify(pagoRepository).flush();
        assertThat(resultado.creado()).isTrue();
        assertThat(resultado.pago().urlPago()).isEqualTo("https://pago.test/cs_test_2");
    }

    @Test
    void iniciar_conUnaReservaQueNoExiste_responde404() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(31L, autenticado))
                .isInstanceOf(ReservaNoEncontradaException.class);

        verifyNoInteractions(pasarela, pagoRepository);
    }

    @Test
    @DisplayName("La reserva de otro Huésped se deniega y el intento queda auditado")
    void iniciar_conReservaAjena_seDeniegaYSeAudita() {
        reservaPropia();
        UsuarioAutenticado otro = new UsuarioAutenticado(99L, "otro@correo.com", Rol.HUESPED,
                INSTANTE.plusSeconds(3600));

        assertThatThrownBy(() -> servicio.iniciar(31L, otro)).isInstanceOf(ReservaAjenaException.class);

        verify(auditoriaService).registrarIntentoFallido(99L, "otro@correo.com", AccionAuditoria.ACCESO_DENEGADO,
                "Reserva", "31", "RESERVA_AJENA");
        verifyNoInteractions(pasarela, pagoRepository);
        assertThat(reserva.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(25));
    }

    @Test
    void iniciar_conReservaConfirmada_noSePermite() {
        reservaPropia();
        reserva.confirmar();
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(31L, autenticado))
                .isInstanceOf(PagoNoPermitidoException.class)
                .hasMessage(PagoServiceImpl.MENSAJE_YA_CONFIRMADA);

        verifyNoInteractions(pasarela);
    }

    @Test
    void iniciar_conReservaCancelada_noSePermite() {
        reservaPropia();
        reserva.vencer();
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(31L, autenticado))
                .isInstanceOf(PagoNoPermitidoException.class)
                .hasMessage(PagoServiceImpl.MENSAJE_NO_ADMITE_PAGO);

        verifyNoInteractions(pasarela);
    }

    @Test
    @DisplayName("Una reserva pendiente cuyo plazo ya terminó no admite pago aunque la tarea no la haya vencido")
    void iniciar_conPlazoTerminado_noSePermite() {
        reservaPropia();
        ReflectionTestUtils.setField(reserva, "fechaExpiracion", AHORA.minusSeconds(1));
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.iniciar(31L, autenticado))
                .isInstanceOf(PagoNoPermitidoException.class)
                .hasMessage(PagoServiceImpl.MENSAJE_NO_ADMITE_PAGO);

        verifyNoInteractions(pasarela);
    }

    @Test
    @DisplayName("Agotados los intentos de pago, no se abren más sesiones")
    void iniciar_conIntentosAgotados_noSePermite() {
        reservaPropia();
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());
        when(pagoRepository.countByReservaIdReserva(31L)).thenReturn(3L);

        assertThatThrownBy(() -> servicio.iniciar(31L, autenticado))
                .isInstanceOf(PagoNoPermitidoException.class)
                .hasMessage(PagoServiceImpl.MENSAJE_INTENTOS_AGOTADOS);

        verifyNoInteractions(pasarela);
        assertThat(reserva.getFechaExpiracion()).isEqualTo(AHORA.plusMinutes(25));
    }

    @Test
    @DisplayName("Si la pasarela falla no se guarda ningún pago ni se audita")
    void iniciar_siLaPasarelaFalla_noGuardaNada() {
        reservaPropia();
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());
        when(pagoRepository.countByReservaIdReserva(31L)).thenReturn(0L);
        when(pasarela.crearSesion(any())).thenThrow(new PasarelaNoDisponibleException());

        assertThatThrownBy(() -> servicio.iniciar(31L, autenticado))
                .isInstanceOf(PasarelaNoDisponibleException.class);

        verify(pagoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("Un título muy largo se recorta para el nombre del producto en Stripe")
    void iniciar_recortaLaDescripcionLarga() {
        reservaPropia();
        ReflectionTestUtils.setField(reserva.getPublicacion(), "titulo", "x".repeat(500));
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());
        when(pagoRepository.countByReservaIdReserva(31L)).thenReturn(0L);
        when(pasarela.crearSesion(any())).thenReturn(
                new SesionCreada("cs_test_1", "https://pago.test/cs_test_1", SESION_VENCE));
        when(pagoRepository.save(any(Pago.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        servicio.iniciar(31L, autenticado);

        ArgumentCaptor<SolicitudSesion> captor = ArgumentCaptor.forClass(SolicitudSesion.class);
        verify(pasarela).crearSesion(captor.capture());
        assertThat(captor.getValue().descripcion()).hasSize(200);
    }

    // ------------------------------------------------------------------ consultar

    @Test
    @DisplayName("Un pago abierto se consulta antes a la pasarela, por si el aviso todavía no llegó")
    void consultar_conPagoAbierto_sincronizaConLaPasarela() {
        Pago pago = pagoAbierto(SESION_VENCE);
        EstadoSesion pagada = new EstadoSesion("cs_test_1", EstadoSesion.Situacion.PAGADA, "pi_1",
                new BigDecimal("875000.00"), "COP");
        when(reservaRepository.findById(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdReservaOrderByIdPagoDesc(31L)).thenReturn(Optional.of(pago));
        when(pasarela.consultarSesion("cs_test_1")).thenReturn(Optional.of(pagada));

        PagoResponse respuesta = servicio.consultar(31L, autenticado);

        verify(resultadoPagoService).sincronizar(31L, pagada);
        assertThat(respuesta.idPago()).isEqualTo(7L);
    }

    @Test
    void consultar_conPagoYaResuelto_noConsultaALaPasarela() {
        Pago pago = pagoAbierto(SESION_VENCE);
        pago.aprobar("pi_1", AHORA);
        when(reservaRepository.findById(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdReservaOrderByIdPagoDesc(31L)).thenReturn(Optional.of(pago));

        PagoResponse respuesta = servicio.consultar(31L, autenticado);

        assertThat(respuesta.estado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(respuesta.urlPago()).isNull();
        verifyNoInteractions(pasarela, resultadoPagoService);
    }

    @Test
    void consultar_siLaPasarelaNoConoceLaSesion_devuelveLoQueHay() {
        Pago pago = pagoAbierto(SESION_VENCE);
        when(reservaRepository.findById(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdReservaOrderByIdPagoDesc(31L)).thenReturn(Optional.of(pago));
        when(pasarela.consultarSesion("cs_test_1")).thenReturn(Optional.empty());

        PagoResponse respuesta = servicio.consultar(31L, autenticado);

        assertThat(respuesta.estado()).isEqualTo(EstadoPago.PENDIENTE);
        verifyNoInteractions(resultadoPagoService);
    }

    @Test
    @DisplayName("Si la pasarela no responde, consultar no falla: devuelve el estado que hay")
    void consultar_siLaPasarelaFalla_devuelveElEstadoActual() {
        Pago pago = pagoAbierto(SESION_VENCE);
        when(reservaRepository.findById(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdReservaOrderByIdPagoDesc(31L)).thenReturn(Optional.of(pago));
        when(pasarela.consultarSesion("cs_test_1")).thenThrow(new PasarelaNoDisponibleException());

        PagoResponse respuesta = servicio.consultar(31L, autenticado);

        assertThat(respuesta.estado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(respuesta.urlPago()).isNotNull();
        verifyNoInteractions(resultadoPagoService);
    }

    @Test
    void consultar_conReservaAjena_seDeniegaYSeAudita() {
        when(reservaRepository.findById(31L)).thenReturn(Optional.of(reserva));
        UsuarioAutenticado otro = new UsuarioAutenticado(99L, "otro@correo.com", Rol.HUESPED,
                INSTANTE.plusSeconds(3600));

        assertThatThrownBy(() -> servicio.consultar(31L, otro)).isInstanceOf(ReservaAjenaException.class);

        verify(auditoriaService).registrarIntentoFallido(99L, "otro@correo.com", AccionAuditoria.ACCESO_DENEGADO,
                "Reserva", "31", "RESERVA_AJENA");
        verifyNoInteractions(pasarela, resultadoPagoService);
    }

    @Test
    void consultar_conUnaReservaQueNoExiste_responde404() {
        when(reservaRepository.findById(31L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(31L, autenticado))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }

    @Test
    void consultar_conReservaSinPago_responde404() {
        when(reservaRepository.findById(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findFirstByReservaIdReservaOrderByIdPagoDesc(31L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.consultar(31L, autenticado))
                .isInstanceOf(PagoNoEncontradoException.class);

        verifyNoInteractions(pasarela);
    }

    // ------------------------------------------------------------------ avisos

    @Test
    void procesarEvento_conFirmaValida_aplicaElResultado() {
        EventoPasarela evento = EventoPasarela.sesionExpirada("evt_1", "cs_test_1");
        when(pasarela.leerEvento("{}", "t=1,v1=ab")).thenReturn(evento);

        servicio.procesarEvento("{}", "t=1,v1=ab");

        verify(resultadoPagoService).aplicarEvento(evento);
    }

    @Test
    @DisplayName("Una firma inválida se registra en el log y el aviso no tiene efecto")
    void procesarEvento_conFirmaInvalida_noAplicaNada() {
        when(pasarela.leerEvento("{}", "basura")).thenThrow(new FirmaWebhookInvalidaException());

        assertThatThrownBy(() -> servicio.procesarEvento("{}", "basura"))
                .isInstanceOf(FirmaWebhookInvalidaException.class);

        verify(auditoriaService).registrarFallo("webhook-stripe", AccionAuditoria.FIRMA_WEBHOOK_INVALIDA, "Pago",
                "FIRMA_INVALIDA");
        verifyNoInteractions(resultadoPagoService);
        verify(auditoriaService, never()).registrarEventoDelSistema(any(), anyString(), anyString(), anyString());
    }
}
