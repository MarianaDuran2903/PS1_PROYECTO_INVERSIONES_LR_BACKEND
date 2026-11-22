package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.PeriodoDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
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

    public DisponibilidadServiceImpl(
            PublicacionService publicacionService,
            PeriodoDisponibilidadRepository periodoRepository
    ) {
        this.publicacionService = publicacionService;
        this.periodoRepository = periodoRepository;
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
}