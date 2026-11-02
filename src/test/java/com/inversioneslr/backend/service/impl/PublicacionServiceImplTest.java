package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.ReferenciaInvalidaException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.PoliticaCancelacionRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicacionServiceImplTest {

    private static final UsuarioAutenticado ANFITRION_AUTENTICADO =
            new UsuarioAutenticado(5L, "carlos@correo.com", Rol.ANFITRION, Instant.parse("2026-11-01T13:00:00Z"));

    @Mock
    private PublicacionRepository publicacionRepository;
    @Mock
    private CiudadRepository ciudadRepository;
    @Mock
    private TipoAlojamientoRepository tipoAlojamientoRepository;
    @Mock
    private PoliticaCancelacionRepository politicaCancelacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private PublicacionServiceImpl servicio;
    private Usuario anfitrion;
    private Ciudad cartagena;
    private TipoAlojamiento apartamento;
    private PoliticaCancelacion flexible;

    @BeforeEach
    void setUp() {
        servicio = new PublicacionServiceImpl(publicacionRepository, ciudadRepository, tipoAlojamientoRepository,
                politicaCancelacionRepository, usuarioRepository, auditoriaService, new PublicacionMapper());
        anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(anfitrion, "idUsuario", 5L);
        cartagena = new Ciudad("Cartagena");
        ReflectionTestUtils.setField(cartagena, "idCiudad", 3L);
        apartamento = new TipoAlojamiento("Apartamento");
        ReflectionTestUtils.setField(apartamento, "idTipo", 1L);
        flexible = instanciarPolitica();
    }

    @Test
    @DisplayName("Criterio 1: con datos válidos la publicación se guarda en BORRADOR")
    void crear_datosValidos_guardaEnBorrador() {
        prepararCreacionExitosa();

        PublicacionResponse respuesta = servicio.crear(request("  Apartamento frente al mar  ", " Bocagrande "),
                ANFITRION_AUTENTICADO);

        Publicacion guardada = publicacionGuardada();
        assertThat(guardada.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(respuesta.idPublicacion()).isEqualTo(12L);
        assertThat(respuesta.titulo()).isEqualTo("Apartamento frente al mar");
        assertThat(respuesta.barrioZona()).isEqualTo("Bocagrande");
        assertThat(respuesta.ciudad().nombre()).isEqualTo("Cartagena");
        assertThat(respuesta.tipo().nombre()).isEqualTo("Apartamento");
        assertThat(respuesta.politica().nombre()).isEqualTo("Flexible");
        assertThat(respuesta.tarifaLimpieza()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Criterio 4: la publicación queda asociada únicamente al Anfitrión que la creó")
    void crear_asociaAlAnfitrionDelToken() {
        prepararCreacionExitosa();

        PublicacionResponse respuesta = servicio.crear(request("Casa campestre", null), ANFITRION_AUTENTICADO);

        assertThat(publicacionGuardada().getAnfitrion()).isSameAs(anfitrion);
        assertThat(respuesta.idAnfitrion()).isEqualTo(5L);
        verify(usuarioRepository).getReferenceById(5L);
    }

    @Test
    @DisplayName("Criterio 4: la creación queda registrada para trazabilidad")
    void crear_registraEventoDeAuditoria() {
        prepararCreacionExitosa();

        servicio.crear(request("Casa campestre", null), ANFITRION_AUTENTICADO);

        verify(auditoriaService).registrarExito(anfitrion, AccionAuditoria.CREAR_PUBLICACION,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, "12");
    }

    @Test
    void crear_barrioEnBlanco_quedaNulo() {
        prepararCreacionExitosa();

        PublicacionResponse respuesta = servicio.crear(request("Casa campestre", "   "), ANFITRION_AUTENTICADO);

        assertThat(respuesta.barrioZona()).isNull();
    }

    @Test
    void crear_ciudadInexistente_lanzaErrorEnElCampoSinGuardar() {
        when(ciudadRepository.findById(3L)).thenReturn(Optional.empty());
        CrearPublicacionRequest request = request("Casa", null);

        assertThatThrownBy(() -> servicio.crear(request, ANFITRION_AUTENTICADO))
                .isInstanceOf(ReferenciaInvalidaException.class)
                .hasMessage("La ciudad no existe")
                .extracting("campo").isEqualTo("idCiudad");

        verify(publicacionRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void crear_tipoInexistente_lanzaErrorEnElCampoSinGuardar() {
        when(ciudadRepository.findById(3L)).thenReturn(Optional.of(cartagena));
        when(tipoAlojamientoRepository.findById(1L)).thenReturn(Optional.empty());
        CrearPublicacionRequest request = request("Casa", null);

        assertThatThrownBy(() -> servicio.crear(request, ANFITRION_AUTENTICADO))
                .isInstanceOf(ReferenciaInvalidaException.class)
                .extracting("campo").isEqualTo("idTipo");

        verify(publicacionRepository, never()).save(any());
    }

    @Test
    void crear_sinPoliticaFlexible_fallaSinGuardar() {
        when(ciudadRepository.findById(3L)).thenReturn(Optional.of(cartagena));
        when(tipoAlojamientoRepository.findById(1L)).thenReturn(Optional.of(apartamento));
        when(politicaCancelacionRepository.findByNombre("Flexible")).thenReturn(Optional.empty());
        CrearPublicacionRequest request = request("Casa", null);

        assertThatThrownBy(() -> servicio.crear(request, ANFITRION_AUTENTICADO))
                .isInstanceOf(IllegalStateException.class);

        verify(publicacionRepository, never()).save(any());
    }

    private void prepararCreacionExitosa() {
        when(ciudadRepository.findById(3L)).thenReturn(Optional.of(cartagena));
        when(tipoAlojamientoRepository.findById(1L)).thenReturn(Optional.of(apartamento));
        when(politicaCancelacionRepository.findByNombre("Flexible")).thenReturn(Optional.of(flexible));
        when(usuarioRepository.getReferenceById(5L)).thenReturn(anfitrion);
        when(publicacionRepository.save(any(Publicacion.class))).thenAnswer(inv -> {
            Publicacion publicacion = inv.getArgument(0);
            ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
            return publicacion;
        });
    }

    private Publicacion publicacionGuardada() {
        ArgumentCaptor<Publicacion> captor = ArgumentCaptor.forClass(Publicacion.class);
        verify(publicacionRepository).save(captor.capture());
        return captor.getValue();
    }

    private static CrearPublicacionRequest request(String titulo, String barrio) {
        return new CrearPublicacionRequest(titulo, "Dos habitaciones y balcón", "Calle 5 # 3-20", 3L, barrio,
                1L, 4, new BigDecimal("250000.00"));
    }

    private static PoliticaCancelacion instanciarPolitica() {
        PoliticaCancelacion politica = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(politica, "idPolitica", 1L);
        ReflectionTestUtils.setField(politica, "nombre", "Flexible");
        return politica;
    }
}
