package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
import com.inversioneslr.backend.dto.request.AbrirPeriodoDisponibilidadRequest;
import com.inversioneslr.backend.dto.response.PeriodoDisponibilidadResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

public interface DisponibilidadService {

    CalendarioDisponibilidadResponse consultarCalendarioPropio(
            Long idPublicacion,
            UsuarioAutenticado anfitrion
    );

    PeriodoDisponibilidadResponse abrirPeriodoPropio(Long idPublicacion,
            AbrirPeriodoDisponibilidadRequest request, UsuarioAutenticado anfitrion);

    PeriodoDisponibilidadResponse cerrarPeriodoPropio(Long idPublicacion, Long idPeriodo,
                                                       UsuarioAutenticado anfitrion);
}
