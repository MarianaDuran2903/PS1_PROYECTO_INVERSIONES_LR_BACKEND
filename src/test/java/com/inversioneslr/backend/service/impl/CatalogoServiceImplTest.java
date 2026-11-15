package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.CatalogoItemResponse;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.Servicio;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.ServicioRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoServiceImplTest {

    @Mock
    private CiudadRepository ciudadRepository;

    @Mock
    private TipoAlojamientoRepository tipoAlojamientoRepository;

    @Mock
    private ServicioRepository servicioRepository;

    private CatalogoServiceImpl servicio;

    @BeforeEach
    void setUp() {
        servicio = new CatalogoServiceImpl(
                ciudadRepository,
                tipoAlojamientoRepository,
                servicioRepository,
                new PublicacionMapper());
    }

    @Test
    void listarCiudades_devuelveIdYNombre() {
        Ciudad bogota = new Ciudad("Bogotá");
        ReflectionTestUtils.setField(bogota, "idCiudad", 1L);

        when(ciudadRepository.findAllByOrderByNombreAsc())
                .thenReturn(List.of(bogota));

        assertThat(servicio.listarCiudades())
                .containsExactly(new CatalogoItemResponse(1L, "Bogotá"));

        verify(ciudadRepository).findAllByOrderByNombreAsc();
    }

    @Test
    void listarTiposAlojamiento_devuelveIdYNombre() {
        TipoAlojamiento casa = new TipoAlojamiento("Casa");
        ReflectionTestUtils.setField(casa, "idTipo", 2L);

        when(tipoAlojamientoRepository.findAllByOrderByNombreAsc())
                .thenReturn(List.of(casa));

        assertThat(servicio.listarTiposAlojamiento())
                .containsExactly(new CatalogoItemResponse(2L, "Casa"));

        verify(tipoAlojamientoRepository).findAllByOrderByNombreAsc();
    }

    @Test
    void listarServicios_devuelveIdYNombreYConservaElOrdenDelRepositorio() {
        Servicio cocina = new Servicio("Cocina");
        ReflectionTestUtils.setField(cocina, "idServicio", 3L);

        Servicio wifi = new Servicio("Wi-Fi");
        ReflectionTestUtils.setField(wifi, "idServicio", 1L);

        when(servicioRepository.findAllByOrderByNombreAsc())
                .thenReturn(List.of(cocina, wifi));

        assertThat(servicio.listarServicios())
                .containsExactly(
                        new CatalogoItemResponse(3L, "Cocina"),
                        new CatalogoItemResponse(1L, "Wi-Fi"));

        verify(servicioRepository).findAllByOrderByNombreAsc();
    }

    @Test
    void listarServicios_sinServicios_devuelveListaVacia() {
        when(servicioRepository.findAllByOrderByNombreAsc())
                .thenReturn(List.of());

        assertThat(servicio.listarServicios()).isEmpty();

        verify(servicioRepository).findAllByOrderByNombreAsc();
    }
}