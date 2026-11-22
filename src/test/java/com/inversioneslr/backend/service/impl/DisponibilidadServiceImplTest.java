package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.CalendarioDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.PeriodoDisponibilidadResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.PublicacionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceImplTest {

    @Mock
    private PublicacionService publicacionService;

    @Mock
    private PeriodoDisponibilidadRepository periodoRepository;

    @Mock
    private PeriodoDisponibilidad periodo;

    @Test
    void consultarCalendarioPropio_verificaPropietarioYDevuelvePeriodos() {
        Long idPublicacion = 12L;
        UsuarioAutenticado anfitrion = new UsuarioAutenticado(
                7L, "anfitrion@correo.com", Rol.ANFITRION, Instant.parse("2026-03-01T00:00:00Z"));
        PublicacionResponse publicacion = new PublicacionResponse(
                idPublicacion, 7L, EstadoPublicacion.BORRADOR, "Casa propia",
                null, null, null, null, null, null, 4,
                new BigDecimal("200000.00"), BigDecimal.ZERO, null, null);
        when(publicacionService.obtenerPropia(idPublicacion, anfitrion)).thenReturn(publicacion);
        when(periodoRepository.buscarCalendarioDePublicacion(idPublicacion)).thenReturn(List.of(periodo));
        when(periodo.getIdPeriodo()).thenReturn(2L);
        when(periodo.getFechaInicio()).thenReturn(LocalDate.parse("2026-03-01"));
        when(periodo.getFechaFin()).thenReturn(LocalDate.parse("2026-03-03"));
        when(periodo.getTipo()).thenReturn("DISPONIBLE");

        CalendarioDisponibilidadResponse respuesta =
                new DisponibilidadServiceImpl(publicacionService, periodoRepository)
                        .consultarCalendarioPropio(idPublicacion, anfitrion);

        assertThat(respuesta.idPublicacion()).isEqualTo(idPublicacion);
        assertThat(respuesta.titulo()).isEqualTo("Casa propia");
        assertThat(respuesta.periodos()).containsExactly(
                new PeriodoDisponibilidadResponse(
                        2L, LocalDate.parse("2026-03-01"), LocalDate.parse("2026-03-03"), "DISPONIBLE"));
        verify(publicacionService).obtenerPropia(idPublicacion, anfitrion);
        verify(periodoRepository).buscarCalendarioDePublicacion(idPublicacion);
    }
}