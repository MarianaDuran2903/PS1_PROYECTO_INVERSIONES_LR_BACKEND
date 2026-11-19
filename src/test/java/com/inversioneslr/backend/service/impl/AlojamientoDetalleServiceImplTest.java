package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.AlojamientoDetalleResponse;
import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.exception.AlojamientoNoDisponibleException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.repository.PublicacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlojamientoDetalleServiceImplTest {

    private static final Long ID_PUBLICACION = 21L;

    @Mock
    private PublicacionRepository publicacionRepository;

    @Mock
    private PublicacionMapper publicacionMapper;

    @Mock
    private Publicacion publicacion;

    @Mock
    private AlojamientoDetalleResponse respuestaEsperada;

    @Captor
    private ArgumentCaptor<List<CatalogoItemResponse>> serviciosCaptor;

    @Captor
    private ArgumentCaptor<
            List<AlojamientoDetalleResponse.ImagenResponse>> imagenesCaptor;

    private AlojamientoServiceImpl servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlojamientoServiceImpl(
                publicacionRepository,
                publicacionMapper);
    }

    @Test
    void obtenerDetalle_convierteRecursosYDevuelveRespuestaDelMapper() {
        PublicacionRepository.ServicioLectura cocina =
                mock(PublicacionRepository.ServicioLectura.class);
        when(cocina.getId()).thenReturn(3L);
        when(cocina.getNombre()).thenReturn("Cocina");

        PublicacionRepository.ServicioLectura wifi =
                mock(PublicacionRepository.ServicioLectura.class);
        when(wifi.getId()).thenReturn(1L);
        when(wifi.getNombre()).thenReturn("Wi-Fi");

        PublicacionRepository.ImagenLectura primera =
                mock(PublicacionRepository.ImagenLectura.class);
        when(primera.getOrden()).thenReturn(1);
        when(primera.getRuta()).thenReturn("/referencias/primera.jpg");

        PublicacionRepository.ImagenLectura segunda =
                mock(PublicacionRepository.ImagenLectura.class);
        when(segunda.getOrden()).thenReturn(2);
        when(segunda.getRuta()).thenReturn("/referencias/segunda.jpg");

        when(publicacionRepository.findByIdPublicacionAndEstado(
                ID_PUBLICACION,
                EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));

        when(publicacionRepository.listarServicios(ID_PUBLICACION))
                .thenReturn(List.of(cocina, wifi));

        when(publicacionRepository.listarImagenes(ID_PUBLICACION))
                .thenReturn(List.of(primera, segunda));

        when(publicacionMapper.toDetallePublico(
                same(publicacion),
                anyList(),
                anyList()))
                .thenReturn(respuestaEsperada);

        AlojamientoDetalleResponse respuesta =
                servicio.obtenerDetalle(ID_PUBLICACION);

        assertThat(respuesta).isSameAs(respuestaEsperada);

        verify(publicacionRepository).findByIdPublicacionAndEstado(
                ID_PUBLICACION,
                EstadoPublicacion.ACTIVA);

        verify(publicacionMapper).toDetallePublico(
                same(publicacion),
                serviciosCaptor.capture(),
                imagenesCaptor.capture());

        assertThat(serviciosCaptor.getValue()).containsExactly(
                new CatalogoItemResponse(3L, "Cocina"),
                new CatalogoItemResponse(1L, "Wi-Fi"));

        assertThat(imagenesCaptor.getValue()).containsExactly(
                new AlojamientoDetalleResponse.ImagenResponse(
                        1,
                        "/referencias/primera.jpg"),
                new AlojamientoDetalleResponse.ImagenResponse(
                        2,
                        "/referencias/segunda.jpg"));
    }

    @Test
    void obtenerDetalle_sinRecursos_enviaListasVaciasAlMapper() {
        when(publicacionRepository.findByIdPublicacionAndEstado(
                ID_PUBLICACION,
                EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));

        when(publicacionRepository.listarServicios(ID_PUBLICACION))
                .thenReturn(List.of());

        when(publicacionRepository.listarImagenes(ID_PUBLICACION))
                .thenReturn(List.of());

        when(publicacionMapper.toDetallePublico(
                same(publicacion),
                anyList(),
                anyList()))
                .thenReturn(respuestaEsperada);

        AlojamientoDetalleResponse respuesta =
                servicio.obtenerDetalle(ID_PUBLICACION);

        assertThat(respuesta).isSameAs(respuestaEsperada);

        verify(publicacionMapper).toDetallePublico(
                same(publicacion),
                serviciosCaptor.capture(),
                imagenesCaptor.capture());

        assertThat(serviciosCaptor.getValue()).isEmpty();
        assertThat(imagenesCaptor.getValue()).isEmpty();
    }

    @Test
    void obtenerDetalle_sinPublicacionActiva_noConsultaRecursos() {
        when(publicacionRepository.findByIdPublicacionAndEstado(
                ID_PUBLICACION,
                EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                servicio.obtenerDetalle(ID_PUBLICACION))
                .isInstanceOf(AlojamientoNoDisponibleException.class)
                .hasMessage("El alojamiento no está disponible");

        verify(publicacionRepository).findByIdPublicacionAndEstado(
                ID_PUBLICACION,
                EstadoPublicacion.ACTIVA);

        verify(publicacionRepository, never())
                .listarServicios(ID_PUBLICACION);

        verify(publicacionRepository, never())
                .listarImagenes(ID_PUBLICACION);

        verifyNoInteractions(publicacionMapper);
    }
}