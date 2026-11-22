package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.model.MotivoRechazoPago;
import com.inversioneslr.backend.model.Pago;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.repository.PagoRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PasarelaPago.EstadoSesion;
import com.inversioneslr.backend.service.PasarelaPago.EventoPasarela;
import com.inversioneslr.backend.service.ResultadoPagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class ResultadoPagoServiceImpl implements ResultadoPagoService {

    static final String ENTIDAD_PAGO = "Pago";
    static final String ENTIDAD_RESERVA = "Reserva";

    private static final Logger log = LoggerFactory.getLogger(ResultadoPagoServiceImpl.class);

    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public ResultadoPagoServiceImpl(ReservaRepository reservaRepository, PagoRepository pagoRepository,
                                    AuditoriaService auditoriaService, Clock clock) {
        this.reservaRepository = reservaRepository;
        this.pagoRepository = pagoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    /**
     * Primero se bloquea la reserva y después se lee el pago: así se lee ya actualizado y quien llegue a la vez
     * (otro aviso, la consulta del Huésped, el vencimiento) espera su turno.
     */
    @Override
    @Transactional
    public void aplicarEvento(EventoPasarela evento) {
        if (evento.tipo() == EventoPasarela.Tipo.IGNORADO) {
            return;
        }
        Optional<Long> idReserva = idReservaDe(evento);
        Optional<Reserva> reserva = idReserva.flatMap(reservaRepository::bloquearPorId);
        Optional<Pago> pago = reserva.flatMap(r -> pagoDe(evento, r.getIdReserva()));
        if (pago.isEmpty()) {
            log.info("Aviso de pago sin pago asociado {} {} {}",
                    kv("accion", "APLICAR_AVISO_PAGO"),
                    kv("resultado", "IGNORADO"),
                    kv("idEvento", evento.idEvento()));
            return;
        }
        if (pagoRepository.registrarEvento(evento.idEvento(), evento.tipo().name(), pago.get().getIdPago()) == 0) {
            log.info("Aviso de pago repetido {} {} {}",
                    kv("accion", "APLICAR_AVISO_PAGO"),
                    kv("resultado", "IGNORADO"),
                    kv("idEvento", evento.idEvento()));
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        switch (evento.tipo()) {
            case PAGO_APROBADO -> aprobar(pago.get(), reserva.get(), evento.idPaymentIntent(), evento.monto(),
                    evento.moneda(), ahora);
            case PAGO_RECHAZADO -> rechazar(pago.get(), evento.codigoRechazo(), evento.idPaymentIntent(), ahora);
            case SESION_EXPIRADA -> expirar(pago.get(), ahora);
            default -> throw new IllegalStateException("Tipo de aviso sin tratamiento: " + evento.tipo());
        }
    }

    @Override
    @Transactional
    public void sincronizar(Long idReserva, EstadoSesion estado) {
        if (estado.situacion() == EstadoSesion.Situacion.ABIERTA) {
            return;
        }
        Optional<Reserva> reserva = reservaRepository.bloquearPorId(idReserva);
        Optional<Pago> pago = reserva.flatMap(r -> pagoRepository.findByReferenciaStripe(estado.idSesion()))
                .filter(p -> p.getReserva().getIdReserva().equals(idReserva));
        if (pago.isEmpty()) {
            return;
        }
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        if (estado.situacion() == EstadoSesion.Situacion.PAGADA) {
            aprobar(pago.get(), reserva.get(), estado.idPaymentIntent(), estado.monto(), estado.moneda(), ahora);
        } else {
            expirar(pago.get(), ahora);
        }
    }

    private Optional<Long> idReservaDe(EventoPasarela evento) {
        if (evento.tipo() == EventoPasarela.Tipo.PAGO_RECHAZADO) {
            return Optional.ofNullable(evento.idReserva());
        }
        return Optional.ofNullable(evento.idSesion()).flatMap(pagoRepository::idReservaDeReferencia);
    }

    private Optional<Pago> pagoDe(EventoPasarela evento, Long idReserva) {
        if (evento.tipo() == EventoPasarela.Tipo.PAGO_RECHAZADO) {
            return pagoRepository.findByReservaIdReservaAndEstadoIn(idReserva, EstadoPago.ABIERTOS);
        }
        return pagoRepository.findByReferenciaStripe(evento.idSesion());
    }

    /**
     * Aprueba el pago y confirma la reserva. Antes comprueba que se cobró lo que vale la reserva: si no, no se
     * confirma nada. Si la reserva ya no espera pago (venció antes de que llegara el aviso), el pago queda
     * aprobado como evidencia, la reserva no se toca y el hecho queda en la auditoría para revisarlo.
     */
    private void aprobar(Pago pago, Reserva reserva, String idPaymentIntent, BigDecimal monto, String moneda,
                         OffsetDateTime ahora) {
        if (pago.getEstado() == EstadoPago.APROBADO) {
            return;
        }
        if (!pago.coincideCon(monto, moneda)) {
            log.error("El monto cobrado no coincide con el de la reserva {} {} {}",
                    kv("accion", "APROBAR_PAGO"),
                    kv("resultado", "FALLIDO"),
                    kv("idPago", pago.getIdPago()));
            if (pago.rechazar(MotivoRechazoPago.MONTO_NO_COINCIDE, idPaymentIntent, ahora)) {
                auditar(AccionAuditoria.RECHAZAR_PAGO, pago, "El monto cobrado no coincide con el de la reserva");
            }
            return;
        }
        pago.aprobar(idPaymentIntent, ahora);
        auditar(AccionAuditoria.APROBAR_PAGO, pago, "Pago aprobado por la pasarela" + referencia(idPaymentIntent));
        if (reserva.estaPendienteDePago()) {
            reserva.confirmar();
            auditoriaService.registrarEventoDelSistema(AccionAuditoria.CONFIRMAR_RESERVA, ENTIDAD_RESERVA,
                    String.valueOf(reserva.getIdReserva()), "Reserva confirmada por el pago " + pago.getIdPago());
            return;
        }
        log.warn("Pago aprobado de una reserva que ya no espera pago {} {} {}",
                kv("accion", "PAGO_SIN_RESERVA_ACTIVA"),
                kv("idPago", pago.getIdPago()),
                kv("estadoReserva", reserva.getEstado()));
        auditoriaService.registrarEventoDelSistema(AccionAuditoria.PAGO_SIN_RESERVA_ACTIVA, ENTIDAD_PAGO,
                String.valueOf(pago.getIdPago()),
                "El pago se aprobó con la reserva " + reserva.getIdReserva() + " en estado " + reserva.getEstado());
    }

    private void rechazar(Pago pago, String codigo, String idPaymentIntent, OffsetDateTime ahora) {
        if (pago.rechazar(codigo, idPaymentIntent, ahora)) {
            auditar(AccionAuditoria.RECHAZAR_PAGO, pago, "Pago rechazado por la pasarela: " + codigo);
        }
    }

    private void expirar(Pago pago, OffsetDateTime ahora) {
        if (pago.expirar(ahora)) {
            auditar(AccionAuditoria.EXPIRAR_PAGO, pago, "La sesión de pago venció sin pago");
        }
    }

    private void auditar(AccionAuditoria accion, Pago pago, String detalle) {
        auditoriaService.registrarEventoDelSistema(accion, ENTIDAD_PAGO, String.valueOf(pago.getIdPago()), detalle);
    }

    private static String referencia(String idPaymentIntent) {
        return idPaymentIntent == null ? "" : " (" + idPaymentIntent + ")";
    }
}
