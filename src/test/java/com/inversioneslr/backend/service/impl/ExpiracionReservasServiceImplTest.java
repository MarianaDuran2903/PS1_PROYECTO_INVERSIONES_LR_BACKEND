package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.DesglosePrecio;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoReserva;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.service.AuditoriaService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiracionReservasServiceImplTest {

    private static final Instant AHORA = Instant.parse("2030-06-01T16:00:00Z");
    private static final OffsetDateTime AHORA_ODT = OffsetDateTime.parse("2030-06-01T16:00:00Z");

    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private PeriodoDisponibilidadRepository periodoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ExpiracionReservasServiceImpl servicio;
    private Publicacion publicacion;
    private Usuario huesped;
    private PoliticaCancelacion politica;

    @BeforeEach
    void setUp() {
        servicio = new ExpiracionReservasServiceImpl(reservaRepository, periodoRepository, auditoriaService,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        huesped = new Usuario(new NombreCompleto("Ana", null, "Pérez", null), "ana@correo.com", "hash", Rol.HUESPED);
        politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(politica, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(politica, "porcentajeDevolucion", new BigDecimal("100.00"));
        publicacion = BeanUtils.instantiateClass(Publicacion.class);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
    }

    @Test
    @DisplayName("Vence cada reserva: queda CANCELADA, libera su tramo y deja el evento del sistema")
    void vencerPendientes_vencePorCompletoCadaReserva() {
        Reserva primera = reservaPendiente(31L, "2030-06-10", "2030-06-13");
        Reserva segunda = reservaPendiente(32L, "2030-07-01", "2030-07-03");
        PeriodoDisponibilidad tramoPrimera = PeriodoDisponibilidad.reservado(publicacion, primera,
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-13"));
        PeriodoDisponibilidad tramoSegunda = PeriodoDisponibilidad.reservado(publicacion, segunda,
                LocalDate.parse("2030-07-01"), LocalDate.parse("2030-07-03"));
        when(reservaRepository.bloquearPendientesVencidas(AHORA_ODT, ExpiracionReservasServiceImpl.TAMANO_LOTE))
                .thenReturn(List.of(primera, segunda));
        when(periodoRepository.findByReservaIdReserva(31L)).thenReturn(Optional.of(tramoPrimera));
        when(periodoRepository.findByReservaIdReserva(32L)).thenReturn(Optional.of(tramoSegunda));

        int vencidas = servicio.vencerPendientes();

        assertThat(vencidas).isEqualTo(2);
        assertThat(primera.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(segunda.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(tramoPrimera.getTipo()).isEqualTo(TipoPeriodo.DISPONIBLE);
        assertThat(tramoPrimera.getReserva()).isNull();
        assertThat(tramoSegunda.getTipo()).isEqualTo(TipoPeriodo.DISPONIBLE);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.VENCER_RESERVA, "Reserva", "31",
                ExpiracionReservasServiceImpl.DETALLE_VENCIMIENTO);
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.VENCER_RESERVA, "Reserva", "32",
                ExpiracionReservasServiceImpl.DETALLE_VENCIMIENTO);
    }

    @Test
    @DisplayName("Una reserva sin tramo reservado también se vence")
    void vencerPendientes_sinTramoReservado_venceLaReserva() {
        Reserva reserva = reservaPendiente(31L, "2030-06-10", "2030-06-13");
        when(reservaRepository.bloquearPendientesVencidas(AHORA_ODT, ExpiracionReservasServiceImpl.TAMANO_LOTE))
                .thenReturn(List.of(reserva));
        when(periodoRepository.findByReservaIdReserva(31L)).thenReturn(Optional.empty());

        int vencidas = servicio.vencerPendientes();

        assertThat(vencidas).isEqualTo(1);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void vencerPendientes_sinReservasCaducadas_noHaceNada() {
        when(reservaRepository.bloquearPendientesVencidas(AHORA_ODT, ExpiracionReservasServiceImpl.TAMANO_LOTE))
                .thenReturn(List.of());

        assertThat(servicio.vencerPendientes()).isZero();

        verifyNoInteractions(periodoRepository, auditoriaService);
    }

    @Test
    @DisplayName("Las de una publicación se vencen y se escriben antes de devolver el control")
    void vencerPendientesDe_vencePorPublicacionYEscribeLosCambios() {
        Reserva reserva = reservaPendiente(31L, "2030-06-10", "2030-06-13");
        PeriodoDisponibilidad tramo = PeriodoDisponibilidad.reservado(publicacion, reserva,
                LocalDate.parse("2030-06-10"), LocalDate.parse("2030-06-13"));
        when(reservaRepository.bloquearPendientesVencidasDe(12L, AHORA_ODT)).thenReturn(List.of(reserva));
        when(periodoRepository.findByReservaIdReserva(31L)).thenReturn(Optional.of(tramo));

        int vencidas = servicio.vencerPendientesDe(12L);

        assertThat(vencidas).isEqualTo(1);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(tramo.getTipo()).isEqualTo(TipoPeriodo.DISPONIBLE);
        verify(reservaRepository).flush();
        verify(auditoriaService).registrarEventoDelSistema(AccionAuditoria.VENCER_RESERVA, "Reserva", "31",
                ExpiracionReservasServiceImpl.DETALLE_VENCIMIENTO);
    }

    @Test
    void vencerPendientesDe_sinReservasCaducadas_noEscribeNada() {
        when(reservaRepository.bloquearPendientesVencidasDe(12L, AHORA_ODT)).thenReturn(List.of());

        assertThat(servicio.vencerPendientesDe(12L)).isZero();

        verify(reservaRepository, never()).flush();
        verifyNoInteractions(periodoRepository, auditoriaService);
    }

    private Reserva reservaPendiente(long id, String llegada, String salida) {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("100000.00"), BigDecimal.ZERO, 2,
                BigDecimal.ZERO, new BigDecimal("10.00"));
        Reserva reserva = Reserva.preReservar(huesped, publicacion, LocalDate.parse(llegada),
                LocalDate.parse(salida), 2, "clave-" + id, precio, politica, AHORA_ODT.minusMinutes(1));
        ReflectionTestUtils.setField(reserva, "idReserva", id);
        return reserva;
    }
}
