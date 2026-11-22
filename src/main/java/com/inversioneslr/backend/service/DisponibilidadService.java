package com.inversioneslr.backend.service;

import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
import com.inversioneslr.backend.security.UsuarioAutenticado;

public interface DisponibilidadService {

    CalendarioDisponibilidadResponse consultarCalendarioPropio(
            Long idPublicacion,
            UsuarioAutenticado anfitrion
    );
}