package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.PublicacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlojamientoServiceImplTest {

    @Mock
    private PublicacionRepository publicacionRepository;

    private AlojamientoServiceImpl servicio;
    private Usuario anfitrion;
    private Ciudad cartagena;
    private TipoAlojamiento apartamento;
    private PoliticaCancelacion flexible;

    @BeforeEach
    void setUp() {
        servicio = new AlojamientoServiceImpl(publicacionRepository, new PublicacionMapper());
        anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(anfitrion, "idUsuario", 5L);
        cartagena = new Ciudad("Cartagena");
        ReflectionTestUtils.setField(cartagena, "idCiudad", 3L);
        apartamento = new TipoAlojamiento("Apartamento");
        ReflectionTestUtils.setField(apartamento, "idTipo", 1L);
        flexible = BeanUtils.instantiateClass(PoliticaCancelacion.class);
    }

    @Test
    @DisplayName("Criterio 2: solo se consultan las publicaciones ACTIVAS, de la más reciente a la más antigua")
    void listarDisponibles_consultaSoloActivasPorMasReciente() {
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.ACTIVA), any(Pageable.class)))
                .thenReturn(Page.empty());

        servicio.listarDisponibles(2, 10);

        ArgumentCaptor<Pageable> pagina = ArgumentCaptor.forClass(Pageable.class);
        verify(publicacionRepository).findByEstado(eq(EstadoPublicacion.ACTIVA), pagina.capture());
        assertThat(pagina.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pagina.getValue().getPageSize()).isEqualTo(10);
        assertThat(pagina.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "idPublicacion"));
    }

    @Test
    @DisplayName("Devuelve el resumen público de cada alojamiento con los totales de la página")
    void listarDisponibles_convierteAResumenConLosTotales() {
        Page<Publicacion> pagina = new PageImpl<>(List.of(publicacion(21L, "Casa en el centro"),
                publicacion(20L, "Apartamento frente al mar")), PageRequest.of(1, 2), 5);
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.ACTIVA), any(Pageable.class)))
                .thenReturn(pagina);

        PaginaResponse<PublicacionResumenResponse> respuesta = servicio.listarDisponibles(1, 2);

        assertThat(respuesta.contenido()).extracting(PublicacionResumenResponse::idPublicacion)
                .containsExactly(21L, 20L);
        PublicacionResumenResponse primero = respuesta.contenido().get(0);
        assertThat(primero.titulo()).isEqualTo("Casa en el centro");
        assertThat(primero.ciudad().nombre()).isEqualTo("Cartagena");
        assertThat(primero.tipo().nombre()).isEqualTo("Apartamento");
        assertThat(primero.capacidad()).isEqualTo(4);
        assertThat(primero.precioNoche()).isEqualByComparingTo("250000.00");
        assertThat(respuesta.pagina()).isEqualTo(1);
        assertThat(respuesta.tamano()).isEqualTo(2);
        assertThat(respuesta.totalElementos()).isEqualTo(5);
        assertThat(respuesta.totalPaginas()).isEqualTo(3);
        assertThat(respuesta.mensaje()).isNull();
    }

    @Test
    @DisplayName("Sin alojamientos disponibles la página viene vacía y con un mensaje")
    void listarDisponibles_sinResultados_informaQueNoHay() {
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.ACTIVA), any(Pageable.class)))
                .thenReturn(Page.empty());

        PaginaResponse<PublicacionResumenResponse> respuesta = servicio.listarDisponibles(0, 20);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.totalElementos()).isZero();
        assertThat(respuesta.mensaje()).isEqualTo("No hay alojamientos disponibles por el momento");
    }

    private Publicacion publicacion(long id, String titulo) {
        DatosPublicacion datos = DatosPublicacion.normalizados(titulo, "Dos habitaciones", "Calle 5 # 3-20",
                "Bocagrande", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", id);
        ReflectionTestUtils.setField(publicacion, "estado", EstadoPublicacion.ACTIVA);
        return publicacion;
    }
}
