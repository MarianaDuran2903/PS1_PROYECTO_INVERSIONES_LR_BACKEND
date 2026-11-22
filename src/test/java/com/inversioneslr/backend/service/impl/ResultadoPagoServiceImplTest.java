package com.inversioneslr.backend.service.impl;

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
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PasarelaPago.EstadoSesion;
import com.inversioneslr.backend.service.PasarelaPago.EventoPasarela;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultadoPagoServiceImplTest {

    private static final Instant INSTANTE = Instant.parse("2030-06-01T10:20:00Z");
    private static final OffsetDateTime AHORA = OffsetDateTime.parse("2030-06-01T10:20:00Z");
    private static final String SESION = "cs_test_1";
    private static final BigDecimal TOTAL = new BigDecimal("875000.00");

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ResultadoPagoServiceImpl servicio;
    private Reserva reserva;
    private Pago pago;

    @BeforeEach
    void setUp() {
        servicio = new ResultadoPagoServiceImpl(reservaRepository, pagoRepository, auditoriaService,
                Clock.fixed(INSTANTE, ZoneOffset.UTC));
        Usuario huesped = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash",
                Rol.HUESPED);
        PoliticaCancelacion politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(politica, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(politica, "porcentajeDevolucion", new BigDecimal("100.00"));
        Publicacion publicacion = BeanUtils.instantiateClass(Publicacion.class);
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"), 3,
                BigDecimal.ZERO, new BigDecimal("10.00"));
        reserva = Reserva.preReservar(huesped, publicacion, LocalDate.parse("2030-06-10"),
                LocalDate.parse("2030-06-13"), 2, "clave-1", precio, politica, AHORA.plusMinutes(20));
        ReflectionTestUtils.setField(reserva, "idReserva", 31L);
        pago = Pago.iniciar(reserva, SESION, "https://pago.test/1", AHORA.plusMinutes(15), "COP",
                AHORA.minusMinutes(20));
        ReflectionTestUtils.setField(pago, "idPago", 7L);
    }

    /** Un aviso de una sesión conocida: la reserva y el pago se encuentran y el aviso no estaba anotado. */
    private void sesionConocida() {
        when(pagoRepository.idReservaDeReferencia(SESION)).thenReturn(Optional.of(31L));
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReferenciaStripe(SESION)).thenReturn(Optional.of(pago));
    }

    private EventoPasarela aprobado(BigDecimal monto, String moneda) {
        return EventoPasarela.pagoAprobado("evt_1", SESION, "pi_1", monto, moneda);
    }

    // ------------------------------------------------------------------ pago aprobado

    @Test
    @DisplayName("Un pago aprobado deja el pago APROBADO, confirma la reserva y deja la evidencia")
    void aplicarEvento_pagoAprobado_confirmaLaReserva() {
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_1", "PAGO_APROBADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(aprobado(TOTAL, "COP"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
        assertThat(pago.getFechaActualizacion()).isEqualTo(AHORA);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.APROBAR_PAGO, "Pago", "7",
                "Pago aprobado por la pasarela (pi_1)");
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.CONFIRMAR_RESERVA, "Reserva", "31",
                "Reserva confirmada por el pago 7");
    }

    @Test
    @DisplayName("Un aviso repetido no vuelve a hacer nada")
    void aplicarEvento_avisoRepetido_noTieneEfecto() {
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_1", "PAGO_APROBADO", 7L)).thenReturn(0);

        servicio.aplicarEvento(aprobado(TOTAL, "COP"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("Un aviso de una sesión que no conocemos se ignora sin anotarlo")
    void aplicarEvento_sesionDesconocida_seIgnora() {
        when(pagoRepository.idReservaDeReferencia("cs_test_otra")).thenReturn(Optional.empty());

        servicio.aplicarEvento(EventoPasarela.pagoAprobado("evt_1", "cs_test_otra", "pi_1", TOTAL, "COP"));

        verify(pagoRepository, never()).registrarEvento(anyString(), anyString(), anyLong());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void aplicarEvento_avisoSinSesion_seIgnora() {
        servicio.aplicarEvento(EventoPasarela.pagoAprobado("evt_1", null, "pi_1", TOTAL, "COP"));

        verifyNoInteractions(reservaRepository, auditoriaService);
        verify(pagoRepository, never()).registrarEvento(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Si la reserva no existe, el aviso se ignora")
    void aplicarEvento_reservaInexistente_seIgnora() {
        when(pagoRepository.idReservaDeReferencia(SESION)).thenReturn(Optional.of(31L));
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.empty());

        servicio.aplicarEvento(aprobado(TOTAL, "COP"));

        verify(pagoRepository, never()).registrarEvento(anyString(), anyString(), anyLong());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void aplicarEvento_eventoIgnorado_noTocaNada() {
        servicio.aplicarEvento(EventoPasarela.ignorado("evt_1"));

        verifyNoInteractions(reservaRepository, pagoRepository, auditoriaService);
    }

    @Test
    @DisplayName("Un monto distinto al de la reserva no la confirma: el pago queda rechazado")
    void aplicarEvento_montoDistinto_noConfirmaLaReserva() {
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_1", "PAGO_APROBADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(aprobado(new BigDecimal("100.00"), "COP"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(pago.getCodigoRechazo()).isEqualTo("MONTO_NO_COINCIDE");
        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.RECHAZAR_PAGO, "Pago", "7",
                "El monto cobrado no coincide con el de la reserva");
        verifyNoMoreInteractions(auditoriaService);
    }

    @Test
    void aplicarEvento_monedaDistinta_noConfirmaLaReserva() {
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_1", "PAGO_APROBADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(aprobado(TOTAL, "USD"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    void aplicarEvento_sinMonto_noConfirmaLaReserva() {
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_1", "PAGO_APROBADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(aprobado(null, "COP"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    @DisplayName("Un pago aprobado con la reserva ya cancelada queda como evidencia y no la confirma")
    void aplicarEvento_pagoConReservaYaNoPendiente_noLaConfirmaYDejaConstancia() {
        reserva.vencer();
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_1", "PAGO_APROBADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(aprobado(TOTAL, "COP"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.APROBAR_PAGO, "Pago", "7",
                "Pago aprobado por la pasarela (pi_1)");
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.PAGO_SIN_RESERVA_ACTIVA, "Pago", "7",
                "El pago se aprobó con la reserva 31 en estado CANCELADA");
        verify(auditoriaService, never()).registrarEventoDelSistema(eq(AccionAuditoria.CONFIRMAR_RESERVA),
                anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Un pago ya aprobado no se vuelve a aprobar ni a auditar")
    void aplicarEvento_pagoYaAprobado_noHaceNada() {
        pago.aprobar("pi_1", AHORA.minusMinutes(1));
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_2", "PAGO_APROBADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(EventoPasarela.pagoAprobado("evt_2", SESION, "pi_1", TOTAL, "COP"));

        assertThat(pago.getFechaActualizacion()).isEqualTo(AHORA.minusMinutes(1));
        verifyNoInteractions(auditoriaService);
    }

    // ------------------------------------------------------------------ pago rechazado

    @Test
    @DisplayName("Un pago rechazado queda RECHAZADO con su código y la reserva sigue pendiente")
    void aplicarEvento_pagoRechazado_noConfirmaLaReserva() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.of(pago));
        when(pagoRepository.registrarEvento("evt_3", "PAGO_RECHAZADO", 7L)).thenReturn(1);

        servicio.aplicarEvento(EventoPasarela.pagoRechazado("evt_3", 31L, "pi_1", "card_declined"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
        assertThat(pago.getCodigoRechazo()).isEqualTo("card_declined");
        assertThat(pago.getIdPaymentIntent()).isEqualTo("pi_1");
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.RECHAZAR_PAGO, "Pago", "7",
                "Pago rechazado por la pasarela: card_declined");
        verifyNoMoreInteractions(auditoriaService);
    }

    @Test
    @DisplayName("Un rechazo sin reserva identificable no es de este sistema y se ignora")
    void aplicarEvento_rechazoSinReserva_seIgnora() {
        servicio.aplicarEvento(EventoPasarela.pagoRechazado("evt_3", null, "pi_1", "card_declined"));

        verifyNoInteractions(reservaRepository, auditoriaService);
        verify(pagoRepository, never()).registrarEvento(anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Un rechazo cuando ya no hay sesión abierta (el pago ya terminó) se ignora")
    void aplicarEvento_rechazoSinSesionAbierta_seIgnora() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReservaIdReservaAndEstadoIn(31L, EstadoPago.ABIERTOS))
                .thenReturn(Optional.empty());

        servicio.aplicarEvento(EventoPasarela.pagoRechazado("evt_3", 31L, "pi_1", "card_declined"));

        verify(pagoRepository, never()).registrarEvento(anyString(), anyString(), anyLong());
        verifyNoInteractions(auditoriaService);
    }

    // ------------------------------------------------------------------ sesión vencida

    @Test
    void aplicarEvento_sesionExpirada_expiraElPagoSinTocarLaReserva() {
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_4", "SESION_EXPIRADA", 7L)).thenReturn(1);

        servicio.aplicarEvento(EventoPasarela.sesionExpirada("evt_4", SESION));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.EXPIRAR_PAGO, "Pago", "7",
                "La sesión de pago venció sin pago");
    }

    @Test
    @DisplayName("Un aviso de sesión vencida no degrada un pago ya aprobado")
    void aplicarEvento_sesionExpiradaConPagoAprobado_noCambia() {
        pago.aprobar("pi_1", AHORA.minusMinutes(1));
        sesionConocida();
        when(pagoRepository.registrarEvento("evt_4", "SESION_EXPIRADA", 7L)).thenReturn(1);

        servicio.aplicarEvento(EventoPasarela.sesionExpirada("evt_4", SESION));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        verifyNoInteractions(auditoriaService);
    }

    // ------------------------------------------------------------------ consulta a la pasarela

    @Test
    void sincronizar_conSesionAbierta_noCambiaNada() {
        servicio.sincronizar(31L, new EstadoSesion(SESION, EstadoSesion.Situacion.ABIERTA, null, null, null));

        verifyNoInteractions(reservaRepository, pagoRepository, auditoriaService);
    }

    @Test
    @DisplayName("Si Stripe informa la sesión pagada, se aprueba el pago y se confirma la reserva")
    void sincronizar_conSesionPagada_confirmaLaReserva() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReferenciaStripe(SESION)).thenReturn(Optional.of(pago));

        servicio.sincronizar(31L, new EstadoSesion(SESION, EstadoSesion.Situacion.PAGADA, "pi_1", TOTAL, "COP"));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.CONFIRMAR_RESERVA, "Reserva", "31",
                "Reserva confirmada por el pago 7");
        verify(pagoRepository, never()).registrarEvento(anyString(), anyString(), anyLong());
    }

    @Test
    void sincronizar_conSesionExpirada_expiraElPago() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReferenciaStripe(SESION)).thenReturn(Optional.of(pago));

        servicio.sincronizar(31L, new EstadoSesion(SESION, EstadoSesion.Situacion.EXPIRADA, null, null, null));

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
    }

    @Test
    @DisplayName("Una sesión que pertenece a otra reserva no se aplica")
    void sincronizar_sesionDeOtraReserva_seIgnora() {
        Reserva otra = BeanUtils.instantiateClass(Reserva.class);
        ReflectionTestUtils.setField(otra, "idReserva", 99L);
        Pago deOtra = Pago.iniciar(otra, SESION, "https://pago.test/1", AHORA.plusMinutes(15), "COP", AHORA);
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReferenciaStripe(SESION)).thenReturn(Optional.of(deOtra));

        servicio.sincronizar(31L, new EstadoSesion(SESION, EstadoSesion.Situacion.PAGADA, "pi_1", TOTAL, "COP"));

        assertThat(deOtra.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void sincronizar_conSesionDesconocidaOReservaInexistente_seIgnora() {
        when(reservaRepository.bloquearPorId(31L)).thenReturn(Optional.of(reserva));
        when(pagoRepository.findByReferenciaStripe(SESION)).thenReturn(Optional.empty());
        when(reservaRepository.bloquearPorId(32L)).thenReturn(Optional.empty());
        EstadoSesion pagada = new EstadoSesion(SESION, EstadoSesion.Situacion.PAGADA, "pi_1", TOTAL, "COP");

        servicio.sincronizar(31L, pagada);
        servicio.sincronizar(32L, pagada);

        verifyNoInteractions(auditoriaService);
    }
}
