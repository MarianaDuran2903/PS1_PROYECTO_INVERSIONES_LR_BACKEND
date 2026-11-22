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
import com.inversioneslr.backend.model.Pago;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPago;
import com.inversioneslr.backend.model.enums.EstadoReserva;
import com.inversioneslr.backend.repository.PagoRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.PagoService;
import com.inversioneslr.backend.service.PasarelaPago;
import com.inversioneslr.backend.service.PasarelaPago.EventoPasarela;
import com.inversioneslr.backend.service.PasarelaPago.SesionCreada;
import com.inversioneslr.backend.service.PasarelaPago.SolicitudSesion;
import com.inversioneslr.backend.service.ResultadoPagoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class PagoServiceImpl implements PagoService {

    static final String ENTIDAD_RESERVA = "Reserva";
    static final String ENTIDAD_PAGO = "Pago";
    static final String MOTIVO_RESERVA_AJENA = "RESERVA_AJENA";
    static final String ORIGEN_WEBHOOK = "webhook-stripe";
    static final String MOTIVO_FIRMA_INVALIDA = "FIRMA_INVALIDA";
    static final String MENSAJE_YA_CONFIRMADA = "La reserva ya está pagada y confirmada";
    static final String MENSAJE_NO_ADMITE_PAGO = "La reserva venció o ya no admite pago; cree una nueva reserva";
    static final String MENSAJE_INTENTOS_AGOTADOS = "Se agotaron los intentos de pago de esta reserva";

    /** Stripe limita el nombre del producto; el título de la publicación se recorta para no pasarse. */
    private static final int LONGITUD_MAXIMA_DESCRIPCION = 200;

    private static final Logger log = LoggerFactory.getLogger(PagoServiceImpl.class);

    private final ReservaRepository reservaRepository;
    private final PagoRepository pagoRepository;
    private final PasarelaPago pasarela;
    private final ResultadoPagoService resultadoPagoService;
    private final AuditoriaService auditoriaService;
    private final PagoMapper pagoMapper;
    private final PagoProperties propiedades;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public PagoServiceImpl(ReservaRepository reservaRepository, PagoRepository pagoRepository,
                           PasarelaPago pasarela, ResultadoPagoService resultadoPagoService,
                           AuditoriaService auditoriaService, PagoMapper pagoMapper, PagoProperties propiedades,
                           Clock clock, TransactionTemplate transaccion) {
        this.reservaRepository = reservaRepository;
        this.pagoRepository = pagoRepository;
        this.pasarela = pasarela;
        this.resultadoPagoService = resultadoPagoService;
        this.auditoriaService = auditoriaService;
        this.pagoMapper = pagoMapper;
        this.propiedades = propiedades;
        this.clock = clock;
        this.transaccion = transaccion;
    }

    /**
     * Se bloquea la reserva: dos solicitudes a la vez se atienden de a una, y la segunda recibe la sesión que
     * abrió la primera en lugar de abrir otra que también podría pagarse. Si la pasarela falla, la transacción se
     * revierte y la reserva queda como estaba.
     */
    @Override
    @Transactional
    public ResultadoInicio iniciar(Long idReserva, UsuarioAutenticado huesped) {
        Reserva reserva = propia(reservaRepository.bloquearPorId(idReserva), idReserva, huesped);
        OffsetDateTime ahora = OffsetDateTime.now(clock);

        Optional<Pago> abierto = pagoRepository.findByReservaIdReservaAndEstadoIn(idReserva, EstadoPago.ABIERTOS);
        if (abierto.isPresent()) {
            Pago pago = abierto.get();
            if (pago.sesionVigente(ahora) && reserva.estaPendienteDePago()) {
                return new ResultadoInicio(pagoMapper.toResponse(pago, ahora), false);
            }
            // La sesión venció y el aviso aún no llegó: se cierra para poder abrir otra.
            pago.expirar(ahora);
            pagoRepository.flush();
        }
        exigirQueAdmitaPago(reserva, ahora);
        if (pagoRepository.countByReservaIdReserva(idReserva) >= propiedades.intentosMaximos()) {
            throw new PagoNoPermitidoException(MENSAJE_INTENTOS_AGOTADOS);
        }

        OffsetDateTime venceEn = ahora.plusMinutes(propiedades.minutosSesion());
        reserva.extenderPlazoHasta(venceEn.plusMinutes(propiedades.minutosGracia()));
        SesionCreada sesion = pasarela.crearSesion(new SolicitudSesion(idReserva, descripcionDe(reserva),
                reserva.getTotal(), propiedades.moneda(), venceEn, propiedades.urlExitoDe(idReserva),
                propiedades.urlCancelacionDe(idReserva)));

        Pago pago = pagoRepository.save(Pago.iniciar(reserva, sesion.idSesion(), sesion.url(), sesion.venceEn(),
                propiedades.moneda(), ahora));
        auditoriaService.registrarExito(reserva.getHuesped(), AccionAuditoria.INICIAR_PAGO, ENTIDAD_PAGO,
                String.valueOf(pago.getIdPago()), "Pago iniciado para la reserva " + idReserva);
        return new ResultadoInicio(pagoMapper.toResponse(pago, ahora), true);
    }

    /**
     * No es transaccional por sí misma: la consulta a la pasarela es una llamada de red y no debe sostener una
     * transacción abierta. Cada tramo de base de datos usa la suya.
     */
    @Override
    public PagoResponse consultar(Long idReserva, UsuarioAutenticado huesped) {
        Optional<String> sesionAbierta = transaccion.execute(estado -> {
            propia(reservaRepository.findById(idReserva), idReserva, huesped);
            Pago ultimo = ultimoPago(idReserva);
            return ultimo.getEstado().estaAbierto() ? Optional.of(ultimo.getReferenciaStripe()) : Optional.empty();
        });
        if (sesionAbierta != null && sesionAbierta.isPresent()) {
            sincronizarConPasarela(idReserva, sesionAbierta.get());
        }
        return transaccion.execute(estado -> pagoMapper.toResponse(ultimoPago(idReserva), OffsetDateTime.now(clock)));
    }

    @Override
    public void procesarEvento(String cuerpo, String firma) {
        EventoPasarela evento;
        try {
            evento = pasarela.leerEvento(cuerpo, firma);
        } catch (FirmaWebhookInvalidaException error) {
            // Solo al log: cualquiera puede enviar basura a esta ruta pública y no debe llenar la base.
            auditoriaService.registrarFallo(ORIGEN_WEBHOOK, AccionAuditoria.FIRMA_WEBHOOK_INVALIDA, ENTIDAD_PAGO,
                    MOTIVO_FIRMA_INVALIDA);
            throw error;
        }
        resultadoPagoService.aplicarEvento(evento);
    }

    /**
     * El pago puede haberse completado sin que el aviso haya llegado todavía. Si la pasarela no responde, se
     * devuelve el estado que hay: consultar no debe fallar por eso.
     */
    private void sincronizarConPasarela(Long idReserva, String idSesion) {
        try {
            pasarela.consultarSesion(idSesion)
                    .ifPresent(estado -> resultadoPagoService.sincronizar(idReserva, estado));
        } catch (PasarelaNoDisponibleException error) {
            log.warn("No se pudo consultar la sesión de pago {} {} {}",
                    kv("accion", "CONSULTAR_PAGO"),
                    kv("resultado", "FALLIDO"),
                    kv("idReserva", idReserva));
        }
    }

    private Pago ultimoPago(Long idReserva) {
        return pagoRepository.findFirstByReservaIdReservaOrderByIdPagoDesc(idReserva)
                .orElseThrow(PagoNoEncontradoException::new);
    }

    /**
     * La reserva del Huésped que la pide. La de otro Huésped se deniega y el intento queda en la auditoría.
     */
    private Reserva propia(Optional<Reserva> reserva, Long idReserva, UsuarioAutenticado huesped) {
        Reserva encontrada = reserva.orElseThrow(ReservaNoEncontradaException::new);
        if (!encontrada.getHuesped().getIdUsuario().equals(huesped.idUsuario())) {
            auditoriaService.registrarIntentoFallido(huesped.idUsuario(), huesped.correo(),
                    AccionAuditoria.ACCESO_DENEGADO, ENTIDAD_RESERVA, String.valueOf(idReserva),
                    MOTIVO_RESERVA_AJENA);
            throw new ReservaAjenaException();
        }
        return encontrada;
    }

    private static void exigirQueAdmitaPago(Reserva reserva, OffsetDateTime ahora) {
        if (reserva.getEstado() == EstadoReserva.CONFIRMADA) {
            throw new PagoNoPermitidoException(MENSAJE_YA_CONFIRMADA);
        }
        if (!reserva.estaPendienteDePago() || reserva.estaVencida(ahora)) {
            throw new PagoNoPermitidoException(MENSAJE_NO_ADMITE_PAGO);
        }
    }

    private static String descripcionDe(Reserva reserva) {
        String descripcion = "Reserva " + reserva.getIdReserva() + ": " + reserva.getPublicacion().getTitulo()
                + " (" + reserva.getFechaLlegada() + " a " + reserva.getFechaSalida() + ")";
        return descripcion.length() <= LONGITUD_MAXIMA_DESCRIPCION
                ? descripcion
                : descripcion.substring(0, LONGITUD_MAXIMA_DESCRIPCION);
    }
}
