package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.CotizarEstanciaRequest;
import com.inversioneslr.backend.dto.response.CotizacionResponse;
import com.inversioneslr.backend.exception.AlojamientoNoDisponibleException;
import com.inversioneslr.backend.exception.CotizacionNoDisponibleException;
import com.inversioneslr.backend.exception.DatosReservaInvalidosException;
import com.inversioneslr.backend.mapper.CotizacionMapper;
import com.inversioneslr.backend.model.ConfiguracionPlataforma;
import com.inversioneslr.backend.model.CotizacionEstancia;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.ServicioAdicional;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import com.inversioneslr.backend.repository.ConfiguracionPlataformaRepository;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.ServicioAdicionalRepository;
import com.inversioneslr.backend.service.CotizacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CotizacionServiceImpl implements CotizacionService {

    static final String CAMPO_SERVICIOS = "serviciosAdicionales";

    private final PublicacionRepository publicacionRepository;
    private final PeriodoDisponibilidadRepository periodoRepository;
    private final ConfiguracionPlataformaRepository configuracionRepository;
    private final ServicioAdicionalRepository servicioAdicionalRepository;
    private final CotizacionMapper cotizacionMapper;
    private final Clock clock;

    public CotizacionServiceImpl(PublicacionRepository publicacionRepository,
                                 PeriodoDisponibilidadRepository periodoRepository,
                                 ConfiguracionPlataformaRepository configuracionRepository,
                                 ServicioAdicionalRepository servicioAdicionalRepository,
                                 CotizacionMapper cotizacionMapper, Clock clock) {
        this.publicacionRepository = publicacionRepository;
        this.periodoRepository = periodoRepository;
        this.configuracionRepository = configuracionRepository;
        this.servicioAdicionalRepository = servicioAdicionalRepository;
        this.cotizacionMapper = cotizacionMapper;
        this.clock = clock;
    }

    /**
     * Solo lee: no bloquea la publicación ni toca el calendario, y no deja auditoría porque no cambia nada. Primero
     * se rechazan los datos inválidos (400) y después, con los datos bien, la falta de disponibilidad (409).
     */
    @Override
    @Transactional(readOnly = true)
    public CotizacionResponse cotizar(CotizarEstanciaRequest request) {
        LocalDate llegada = request.fechaLlegada();
        LocalDate salida = request.fechaSalida();
        ReglasDeEstadia.exigirLlegadaDesdeHoy(clock, llegada);

        Publicacion publicacion = publicacionRepository
                .findByIdPublicacionAndEstado(request.idPublicacion(), EstadoPublicacion.ACTIVA)
                .orElseThrow(AlojamientoNoDisponibleException::new);
        if (request.huespedes() != null) {
            ReglasDeEstadia.exigirCapacidad(publicacion, request.huespedes());
        }
        List<ServicioAdicional> servicios = serviciosElegidos(publicacion, request.serviciosAdicionales());

        // Una reserva pendiente ya vencida que la tarea programada aún no libera sigue ocupando sus noches:
        // cotizar no modifica nada, así que prefiere no prometer lo que luego no se podría reservar.
        List<PeriodoDisponibilidad> libres = periodoRepository.buscarQueCruzan(publicacion.getIdPublicacion(),
                TipoPeriodo.DISPONIBLE, llegada, salida);
        if (!PeriodoDisponibilidad.cubren(libres, llegada, salida)) {
            throw new CotizacionNoDisponibleException();
        }

        long noches = ChronoUnit.DAYS.between(llegada, salida);
        ConfiguracionPlataforma configuracion = configuracionRepository.findById(ConfiguracionPlataforma.ID_UNICO)
                .orElseThrow(() -> new IllegalStateException("No existe la configuración de la plataforma"));
        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(publicacion.getPrecioNoche(),
                publicacion.getTarifaLimpieza(), noches, configuracion.porcentajeDescuentoPara(noches),
                configuracion.getPorcentajeTarifaServicio(), servicios);
        return cotizacionMapper.toResponse(publicacion.getIdPublicacion(), llegada, salida, cotizacion);
    }

    /**
     * Los servicios pedidos, en el orden en que se pidieron. Un nombre repetido o que la publicación no ofrece es
     * un error del cliente; sin servicios pedidos no se consulta nada.
     */
    private List<ServicioAdicional> serviciosElegidos(Publicacion publicacion, List<String> nombres) {
        if (nombres == null || nombres.isEmpty()) {
            return List.of();
        }
        List<String> pedidos = nombres.stream().map(String::strip).toList();
        if (new HashSet<>(pedidos).size() != pedidos.size()) {
            throw new DatosReservaInvalidosException(CAMPO_SERVICIOS, "No repita servicios adicionales");
        }
        Map<String, ServicioAdicional> ofrecidos = servicioAdicionalRepository
                .findByIdIdPublicacionAndIdNombreIn(publicacion.getIdPublicacion(), pedidos).stream()
                .collect(Collectors.toMap(ServicioAdicional::getNombre, Function.identity()));
        return pedidos.stream()
                .map(nombre -> {
                    ServicioAdicional servicio = ofrecidos.get(nombre);
                    if (servicio == null) {
                        throw new DatosReservaInvalidosException(CAMPO_SERVICIOS,
                                "El servicio adicional \"" + nombre + "\" no está disponible en este alojamiento");
                    }
                    return servicio;
                })
                .toList();
    }
}
