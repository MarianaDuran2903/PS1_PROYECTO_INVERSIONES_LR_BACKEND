package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.PeriodoDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.dto.request.AbrirPeriodoDisponibilidadRequest;
import com.inversioneslr.backend.exception.EstadoNoEditableException;
import com.inversioneslr.backend.exception.FechasNoDisponiblesException;
import com.inversioneslr.backend.exception.PublicacionNoEncontradaException;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.DisponibilidadService;
import com.inversioneslr.backend.service.PublicacionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DisponibilidadServiceImpl implements DisponibilidadService {

    private final PublicacionService publicacionService;
    private final PeriodoDisponibilidadRepository periodoRepository;
    private final PublicacionRepository publicacionRepository;

    public DisponibilidadServiceImpl(
            PublicacionService publicacionService,
            PeriodoDisponibilidadRepository periodoRepository,
            PublicacionRepository publicacionRepository
    ) {
        this.publicacionService = publicacionService;
        this.periodoRepository = periodoRepository;
        this.publicacionRepository = publicacionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarioDisponibilidadResponse consultarCalendarioPropio(
            Long idPublicacion,
            UsuarioAutenticado anfitrion
    ) {
        PublicacionResponse publicacion =
                publicacionService.obtenerPropia(idPublicacion, anfitrion);

        List<PeriodoDisponibilidadResponse> periodos =
                periodoRepository.buscarCalendarioDePublicacion(idPublicacion)
                        .stream()
                        .map(PeriodoDisponibilidadResponse::desde)
                        .toList();

        return new CalendarioDisponibilidadResponse(
                publicacion.idPublicacion(),
                publicacion.titulo(),
                periodos
        );
    }

    @Override
    @Transactional
    public PeriodoDisponibilidadResponse abrirPeriodoPropio(Long idPublicacion,
            AbrirPeriodoDisponibilidadRequest request, UsuarioAutenticado anfitrion) {
        PublicacionResponse propia = publicacionService.obtenerPropia(idPublicacion, anfitrion);
        if (propia.estado() == EstadoPublicacion.BLOQUEADA) {
            throw new EstadoNoEditableException(propia.estado());
        }
        if (periodoRepository.existeSolape(idPublicacion, request.fechaInicio(), request.fechaFin())) {
            throw new FechasNoDisponiblesException();
        }
        var publicacion = publicacionRepository.getReferenceById(idPublicacion);
        PeriodoDisponibilidad periodo = PeriodoDisponibilidad.disponible(
                publicacion, request.fechaInicio(), request.fechaFin());
        return PeriodoDisponibilidadResponse.desde(periodoRepository.saveAndFlush(periodo));
    }

    @Override
    @Transactional
    public PeriodoDisponibilidadResponse cerrarPeriodoPropio(Long idPublicacion, Long idPeriodo,
                                                              UsuarioAutenticado anfitrion) {
        publicacionService.obtenerPropia(idPublicacion, anfitrion);

        // Usa el mismo bloqueo de publicación que la reserva: se vuelve a validar el periodo tras esperar
        // cualquier reserva concurrente, evitando cerrar fechas recién ocupadas.
        Publicacion publicacion = publicacionRepository.bloquearPorId(idPublicacion)
                .orElseThrow(PublicacionNoEncontradaException::new);
        if (publicacion.getEstado() == EstadoPublicacion.BLOQUEADA) {
            throw new EstadoNoEditableException(publicacion.getEstado());
        }

        PeriodoDisponibilidad periodo = periodoRepository
                .bloquearPorIdYPublicacion(idPeriodo, idPublicacion)
                .orElseThrow(FechasNoDisponiblesException::new);
        if (periodo.getTipo() != TipoPeriodo.DISPONIBLE || periodo.getReserva() != null) {
            throw new FechasNoDisponiblesException();
        }
        periodo.cerrarComoNoOfrecido();
        return PeriodoDisponibilidadResponse.desde(periodoRepository.saveAndFlush(periodo));
    }
}
