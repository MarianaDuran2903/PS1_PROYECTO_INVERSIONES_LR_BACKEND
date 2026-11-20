package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.config.ReservaProperties;
import com.inversioneslr.backend.dto.request.CrearReservaRequest;
import com.inversioneslr.backend.exception.AlojamientoNoDisponibleException;
import com.inversioneslr.backend.exception.ClaveIdempotenciaReutilizadaException;
import com.inversioneslr.backend.exception.DatosReservaInvalidosException;
import com.inversioneslr.backend.exception.FechasNoDisponiblesException;
import com.inversioneslr.backend.mapper.ReservaMapper;
import com.inversioneslr.backend.model.ConfiguracionPlataforma;
import com.inversioneslr.backend.model.DesglosePrecio;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.Reserva;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import com.inversioneslr.backend.repository.ConfiguracionPlataformaRepository;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.ReservaRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import com.inversioneslr.backend.service.ExpiracionReservasService;
import com.inversioneslr.backend.service.ReservaService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReservaServiceImpl implements ReservaService {

    static final String ENTIDAD_RESERVA = "Reserva";
    static final String MOTIVO_FECHAS_NO_DISPONIBLES = "FECHAS_NO_DISPONIBLES";
    static final String MOTIVO_CLAVE_REUTILIZADA = "CLAVE_IDEMPOTENCIA_REUTILIZADA";

    /** "Hoy" se cuenta en la zona del negocio: a las 8 p. m. en Colombia ya es el día siguiente en UTC. */
    static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Bogota");

    private static final String RESTRICCION_RESERVA_SIN_SOLAPE = "ex_reserva_sin_solape";
    private static final String RESTRICCION_PERIODO_SIN_SOLAPE = "ex_periodo_sin_solape";
    private static final String RESTRICCION_IDEMPOTENCIA = "uq_reserva_idempotencia";

    private final PublicacionRepository publicacionRepository;
    private final ReservaRepository reservaRepository;
    private final PeriodoDisponibilidadRepository periodoRepository;
    private final ConfiguracionPlataformaRepository configuracionRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final ExpiracionReservasService expiracionReservasService;
    private final ReservaMapper reservaMapper;
    private final ReservaProperties reservaProperties;
    private final Clock clock;
    private final TransactionTemplate transaccion;

    public ReservaServiceImpl(PublicacionRepository publicacionRepository, ReservaRepository reservaRepository,
                              PeriodoDisponibilidadRepository periodoRepository,
                              ConfiguracionPlataformaRepository configuracionRepository,
                              UsuarioRepository usuarioRepository, AuditoriaService auditoriaService,
                              ExpiracionReservasService expiracionReservasService, ReservaMapper reservaMapper,
                              ReservaProperties reservaProperties, Clock clock, TransactionTemplate transaccion) {
        this.publicacionRepository = publicacionRepository;
        this.reservaRepository = reservaRepository;
        this.periodoRepository = periodoRepository;
        this.configuracionRepository = configuracionRepository;
        this.usuarioRepository = usuarioRepository;
        this.auditoriaService = auditoriaService;
        this.expiracionReservasService = expiracionReservasService;
        this.reservaMapper = reservaMapper;
        this.reservaProperties = reservaProperties;
        this.clock = clock;
        this.transaccion = transaccion;
    }

    /**
     * No es transaccional por sí misma: las restricciones de la base (doble reserva, clave repetida) fallan
     * dentro de la transacción y la dejan inutilizable, así que el conflicto se resuelve aquí, ya revertida.
     */
    @Override
    public ResultadoCreacion crear(CrearReservaRequest request, String claveIdempotencia,
                                   UsuarioAutenticado huesped) {
        String clave = claveIdempotencia.strip();
        try {
            return transaccion.execute(estado -> crearEnTransaccion(request, clave, huesped));
        } catch (DataIntegrityViolationException error) {
            return resolverConflicto(error, request, clave, huesped);
        }
    }

    private ResultadoCreacion crearEnTransaccion(CrearReservaRequest request, String clave,
                                                 UsuarioAutenticado autenticado) {
        // Una repetición ya procesada se responde sin tocar nada más, aunque la publicación haya cambiado.
        Optional<Reserva> previa = buscarPrevia(clave, autenticado);
        if (previa.isPresent()) {
            return repetir(previa.get(), request, autenticado);
        }

        LocalDate llegada = request.fechaLlegada();
        LocalDate salida = request.fechaSalida();
        exigirLlegadaDesdeHoy(llegada);

        // Se bloquea la publicación: quien reserva después espera y ya ve el calendario que dejó el anterior.
        Publicacion publicacion = publicacionRepository
                .bloquearPorIdYEstado(request.idPublicacion(), EstadoPublicacion.ACTIVA)
                .orElseThrow(AlojamientoNoDisponibleException::new);

        // Si la misma solicitud llegó dos veces a la vez, la segunda esperó el bloqueo mientras la primera
        // reservaba: ahora las noches ya son de esa reserva y hay que devolverla, no rechazarlas.
        previa = buscarPrevia(clave, autenticado);
        if (previa.isPresent()) {
            return repetir(previa.get(), request, autenticado);
        }
        exigirCapacidad(publicacion, request.huespedes());

        // Una reserva vencida cuya tarea programada aún no corrió no debe estorbar a quien pide esas fechas.
        expiracionReservasService.vencerPendientesDe(publicacion.getIdPublicacion());

        List<PeriodoDisponibilidad> libres = periodoRepository.buscarQueCruzan(publicacion.getIdPublicacion(),
                TipoPeriodo.DISPONIBLE, llegada, salida);
        long noches = ChronoUnit.DAYS.between(llegada, salida);
        if (nochesCubiertas(libres, llegada, salida) != noches) {
            throw rechazoPorFechas(autenticado);
        }

        ConfiguracionPlataforma configuracion = configuracionRepository.findById(ConfiguracionPlataforma.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("No existe la configuración de la plataforma"));
        DesglosePrecio precio = DesglosePrecio.calcular(publicacion.getPrecioNoche(),
                publicacion.getTarifaLimpieza(), noches, configuracion.porcentajeDescuentoPara(noches),
                configuracion.getPorcentajeTarifaServicio());

        // El huésped es siempre el del token, nunca un dato del cuerpo.
        Usuario huesped = usuarioRepository.getReferenceById(autenticado.idUsuario());
        OffsetDateTime venceEn = OffsetDateTime.now(clock).plusMinutes(reservaProperties.minutosVigencia());
        Reserva reserva = Reserva.preReservar(huesped, publicacion, llegada, salida, request.huespedes(), clave,
                precio, publicacion.getPolitica(), venceEn);

        // La restricción de la base es la última defensa contra la doble reserva: si otra ocupa esas noches por
        // un camino que el calendario no refleja, este guardado falla y la transacción se revierte.
        reservaRepository.saveAndFlush(reserva);
        ocuparCalendario(publicacion, reserva, libres);
        auditoriaService.registrarExito(huesped, AccionAuditoria.CREAR_RESERVA, ENTIDAD_RESERVA,
                String.valueOf(reserva.getIdReserva()));
        return new ResultadoCreacion(reservaMapper.toResponse(reserva), true);
    }

    private Optional<Reserva> buscarPrevia(String clave, UsuarioAutenticado autenticado) {
        return reservaRepository.findByHuespedIdUsuarioAndClaveIdempotencia(autenticado.idUsuario(), clave);
    }

    /**
     * La misma clave con la misma solicitud devuelve la reserva original; con otra solicitud es un error del
     * cliente, porque una clave identifica una sola.
     */
    private ResultadoCreacion repetir(Reserva previa, CrearReservaRequest request, UsuarioAutenticado autenticado) {
        if (!previa.coincideCon(request.idPublicacion(), request.fechaLlegada(), request.fechaSalida(),
                request.huespedes())) {
            auditoriaService.registrarFallo(autenticado.correo(), AccionAuditoria.CREAR_RESERVA, ENTIDAD_RESERVA,
                    MOTIVO_CLAVE_REUTILIZADA);
            throw new ClaveIdempotenciaReutilizadaException();
        }
        return new ResultadoCreacion(reservaMapper.toResponse(previa), false);
    }

    private ResultadoCreacion resolverConflicto(DataIntegrityViolationException error, CrearReservaRequest request,
                                                String clave, UsuarioAutenticado autenticado) {
        String detalle = detalleDe(error);
        if (detalle.contains(RESTRICCION_RESERVA_SIN_SOLAPE) || detalle.contains(RESTRICCION_PERIODO_SIN_SOLAPE)) {
            throw rechazoPorFechas(autenticado);
        }
        if (detalle.contains(RESTRICCION_IDEMPOTENCIA)) {
            // Otra solicitud con la misma clave ganó la carrera: se responde lo que esa dejó guardado.
            return transaccion.execute(estado -> buscarPrevia(clave, autenticado)
                    .map(previa -> repetir(previa, request, autenticado))
                    .orElseThrow(() -> error));
        }
        throw error;
    }

    private void exigirLlegadaDesdeHoy(LocalDate llegada) {
        LocalDate hoy = LocalDate.now(clock.withZone(ZONA_NEGOCIO));
        if (llegada.isBefore(hoy)) {
            throw new DatosReservaInvalidosException("fechaLlegada", "La fecha de llegada no puede ser anterior a hoy");
        }
    }

    private void exigirCapacidad(Publicacion publicacion, int huespedes) {
        if (huespedes > publicacion.getCapacidad()) {
            throw new DatosReservaInvalidosException("huespedes",
                    "El alojamiento admite como máximo " + publicacion.getCapacidad() + " huéspedes");
        }
    }

    /**
     * Los tramos libres no se superponen entre sí, así que si suman las noches pedidas las cubren todas, sin
     * huecos.
     */
    private static long nochesCubiertas(List<PeriodoDisponibilidad> libres, LocalDate llegada, LocalDate salida) {
        return libres.stream().mapToLong(periodo -> periodo.nochesEnComun(llegada, salida)).sum();
    }

    /**
     * Reemplaza los tramos libres que tocan la estadía por: lo que sobra antes de la llegada, el tramo reservado
     * y lo que sobra después de la salida. La verificación de superposición se pospone al commit porque, al
     * escribir, el orden de las sentencias no está bajo nuestro control.
     */
    private void ocuparCalendario(Publicacion publicacion, Reserva reserva, List<PeriodoDisponibilidad> libres) {
        LocalDate llegada = reserva.getFechaLlegada();
        LocalDate salida = reserva.getFechaSalida();
        LocalDate inicioLibre = libres.get(0).getFechaInicio();
        LocalDate finLibre = libres.get(libres.size() - 1).getFechaFin();

        periodoRepository.diferirVerificacionDeSolape();
        periodoRepository.deleteAll(libres);
        if (inicioLibre.isBefore(llegada)) {
            periodoRepository.save(PeriodoDisponibilidad.disponible(publicacion, inicioLibre, llegada));
        }
        periodoRepository.save(PeriodoDisponibilidad.reservado(publicacion, reserva, llegada, salida));
        if (finLibre.isAfter(salida)) {
            periodoRepository.save(PeriodoDisponibilidad.disponible(publicacion, salida, finLibre));
        }
    }

    private FechasNoDisponiblesException rechazoPorFechas(UsuarioAutenticado huesped) {
        auditoriaService.registrarFallo(huesped.correo(), AccionAuditoria.CREAR_RESERVA, ENTIDAD_RESERVA,
                MOTIVO_FECHAS_NO_DISPONIBLES);
        return new FechasNoDisponiblesException();
    }

    private static String detalleDe(DataIntegrityViolationException error) {
        String causa = error.getMostSpecificCause().getMessage();
        return Objects.requireNonNullElse(causa, Objects.requireNonNullElse(error.getMessage(), ""));
    }
}
