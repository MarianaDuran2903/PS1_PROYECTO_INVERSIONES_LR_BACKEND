package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.repository.PagoRepository;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.ExpiracionReservasService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class ExpiracionReservasServiceImpl implements ExpiracionReservasService {

    static final String ENTIDAD_RESERVA = "Reserva";
    static final String DETALLE_VENCIMIENTO = "Venció el plazo para pagar la reserva";
    /** Reservas que se vencen por ejecución; el resto queda para la siguiente. */
    static final int TAMANO_LOTE = 100;

    private final ReservaRepository reservaRepository;
    private final PeriodoDisponibilidadRepository periodoRepository;
    private final PagoRepository pagoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public ExpiracionReservasServiceImpl(ReservaRepository reservaRepository,
                                         PeriodoDisponibilidadRepository periodoRepository,
                                         PagoRepository pagoRepository, AuditoriaService auditoriaService,
                                         Clock clock) {
        this.reservaRepository = reservaRepository;
        this.periodoRepository = periodoRepository;
        this.pagoRepository = pagoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int vencerPendientes() {
        List<Reserva> vencidas = reservaRepository.bloquearPendientesVencidas(OffsetDateTime.now(clock), TAMANO_LOTE);
        vencidas.forEach(this::vencer);
        return vencidas.size();
    }

    @Override
    @Transactional
    public int vencerPendientesDe(Long idPublicacion) {
        List<Reserva> vencidas = reservaRepository.bloquearPendientesVencidasDe(idPublicacion,
                OffsetDateTime.now(clock));
        if (vencidas.isEmpty()) {
            return 0;
        }
        vencidas.forEach(this::vencer);
        // Quien llama inserta enseguida una reserva sobre estas fechas: la base debe ver primero las liberaciones.
        reservaRepository.flush();
        return vencidas.size();
    }

    private void vencer(Reserva reserva) {
        reserva.vencer();
        // Una sesión de pago abierta ya no sirve: pagarla no podría confirmar una reserva cancelada.
        pagoRepository.findByReservaIdReservaAndEstadoIn(reserva.getIdReserva(), EstadoPago.ABIERTOS)
                .ifPresent(pago -> pago.expirar(OffsetDateTime.now(clock)));
        periodoRepository.findByReservaIdReserva(reserva.getIdReserva()).ifPresent(PeriodoDisponibilidad::liberar);
        auditoriaService.registrarEventoDelSistema(AccionAuditoria.VENCER_RESERVA, ENTIDAD_RESERVA,
                String.valueOf(reserva.getIdReserva()), DETALLE_VENCIMIENTO);
    }
}
