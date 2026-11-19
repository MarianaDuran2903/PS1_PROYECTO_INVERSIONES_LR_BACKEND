package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
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
import com.inversioneslr.backend.repository.CiudadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.TipoAlojamientoRepository;
import com.inversioneslr.backend.repository.UsuarioRepository;
import com.inversioneslr.backend.security.UsuarioAutenticado;
import com.inversioneslr.backend.service.AuditoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
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
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListadoAdminPublicacionesServiceImplTest {

    private static final UsuarioAutenticado ADMINISTRADOR_AUTENTICADO =
            new UsuarioAutenticado(1L, "admin@correo.com", Rol.ADMINISTRADOR, Instant.parse("2026-11-01T13:00:00Z"));
    private static final Sort MAS_RECIENTES_PRIMERO = Sort.by(Sort.Direction.DESC, "idPublicacion");

    @Mock
    private PublicacionRepository publicacionRepository;
    @Mock
    private CiudadRepository ciudadRepository;
    @Mock
    private TipoAlojamientoRepository tipoAlojamientoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Captor
    private ArgumentCaptor<Pageable> paginaPedida;

    private PublicacionAdminServiceImpl servicio;
    private Usuario anfitrion;
    private Ciudad cartagena;
    private TipoAlojamiento apartamento;
    private PoliticaCancelacion flexible;

    @BeforeEach
    void setUp() {
        servicio = new PublicacionAdminServiceImpl(publicacionRepository, ciudadRepository,
                tipoAlojamientoRepository, usuarioRepository, auditoriaService, new PublicacionMapper());
        anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(anfitrion, "idUsuario", 5L);
        cartagena = new Ciudad("Cartagena");
        ReflectionTestUtils.setField(cartagena, "idCiudad", 3L);
        apartamento = new TipoAlojamiento("Apartamento");
        ReflectionTestUtils.setField(apartamento, "idTipo", 1L);
        flexible = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(flexible, "idPolitica", 1L);
        ReflectionTestUtils.setField(flexible, "nombre", "Flexible");
    }

    @Test
    @DisplayName("Con un estado consulta solo ese estado, de la más reciente a la más antigua")
    void listar_conEstado_consultaPorEstadoDeLaMasRecienteALaMasAntigua() {
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.PAUSADA), any(Pageable.class)))
                .thenReturn(Page.empty());

        servicio.listar(ADMINISTRADOR_AUTENTICADO, EstadoPublicacion.PAUSADA, 2, 10);

        verify(publicacionRepository).findByEstado(eq(EstadoPublicacion.PAUSADA), paginaPedida.capture());
        assertThat(paginaPedida.getValue().getPageNumber()).isEqualTo(2);
        assertThat(paginaPedida.getValue().getPageSize()).isEqualTo(10);
        assertThat(paginaPedida.getValue().getSort()).isEqualTo(MAS_RECIENTES_PRIMERO);
        verify(publicacionRepository, never()).findAllBy(any());
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(EstadoPublicacion.class)
    @DisplayName("Cada estado se consulta por su propio filtro")
    void listar_cadaEstado_seFiltraPorEseEstado(EstadoPublicacion estado) {
        when(publicacionRepository.findByEstado(eq(estado), any(Pageable.class))).thenReturn(Page.empty());

        servicio.listar(ADMINISTRADOR_AUTENTICADO, estado, 0, 20);

        verify(publicacionRepository).findByEstado(eq(estado), any(Pageable.class));
    }

    @Test
    @DisplayName("Sin estado consulta todas las publicaciones, de la más reciente a la más antigua")
    void listar_sinEstado_consultaTodas() {
        when(publicacionRepository.findAllBy(any(Pageable.class))).thenReturn(Page.empty());

        servicio.listar(ADMINISTRADOR_AUTENTICADO, null, 1, 5);

        verify(publicacionRepository).findAllBy(paginaPedida.capture());
        assertThat(paginaPedida.getValue().getPageNumber()).isEqualTo(1);
        assertThat(paginaPedida.getValue().getPageSize()).isEqualTo(5);
        assertThat(paginaPedida.getValue().getSort()).isEqualTo(MAS_RECIENTES_PRIMERO);
        verify(publicacionRepository, never()).findByEstado(any(), any());
    }

    @Test
    @DisplayName("Devuelve la información completa de cada publicación con los totales de la página")
    void listar_convierteConLaInformacionCompleta() {
        Page<Publicacion> pagina = new PageImpl<>(List.of(
                publicacion(21L, EstadoPublicacion.ACTIVA, null),
                publicacion(20L, EstadoPublicacion.PAUSADA, null)), PageRequest.of(1, 2), 5);
        when(publicacionRepository.findAllBy(any(Pageable.class))).thenReturn(pagina);

        PaginaResponse<PublicacionResponse> respuesta = servicio.listar(ADMINISTRADOR_AUTENTICADO, null, 1, 2);

        assertThat(respuesta.contenido()).extracting(PublicacionResponse::idPublicacion).containsExactly(21L, 20L);
        PublicacionResponse primera = respuesta.contenido().get(0);
        assertThat(primera.idAnfitrion()).isEqualTo(5L);
        assertThat(primera.estado()).isEqualTo(EstadoPublicacion.ACTIVA);
        assertThat(primera.direccionTextual()).isEqualTo("Calle 5 # 3-20");
        assertThat(primera.reglas()).isEqualTo("No fumar");
        assertThat(primera.politica().nombre()).isEqualTo("Flexible");
        assertThat(respuesta.pagina()).isEqualTo(1);
        assertThat(respuesta.tamano()).isEqualTo(2);
        assertThat(respuesta.totalElementos()).isEqualTo(5);
        assertThat(respuesta.totalPaginas()).isEqualTo(3);
        assertThat(respuesta.mensaje()).isNull();
    }

    @Test
    @DisplayName("Una publicación bloqueada trae el motivo del bloqueo y las demás no")
    void listar_bloqueada_traeElMotivoDelBloqueo() {
        Page<Publicacion> pagina = new PageImpl<>(List.of(
                publicacion(21L, EstadoPublicacion.BLOQUEADA, "Las fotos pertenecen a otro alojamiento"),
                publicacion(20L, EstadoPublicacion.PAUSADA, null)), PageRequest.of(0, 20), 2);
        when(publicacionRepository.findAllBy(any(Pageable.class))).thenReturn(pagina);

        PaginaResponse<PublicacionResponse> respuesta = servicio.listar(ADMINISTRADOR_AUTENTICADO, null, 0, 20);

        assertThat(respuesta.contenido().get(0).motivoBloqueo())
                .isEqualTo("Las fotos pertenecen a otro alojamiento");
        assertThat(respuesta.contenido().get(1).motivoBloqueo()).isNull();
    }

    @Test
    @DisplayName("Sin publicaciones la página viene vacía y con un mensaje")
    void listar_sinResultados_informaElMensaje() {
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.BLOQUEADA), any(Pageable.class)))
                .thenReturn(Page.empty());

        PaginaResponse<PublicacionResponse> respuesta =
                servicio.listar(ADMINISTRADOR_AUTENTICADO, EstadoPublicacion.BLOQUEADA, 0, 20);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.totalElementos()).isZero();
        assertThat(respuesta.mensaje()).isEqualTo(PublicacionAdminServiceImpl.MENSAJE_SIN_PUBLICACIONES);
    }

    private Publicacion publicacion(long id, EstadoPublicacion estado, String motivoBloqueo) {
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "Bocagrande", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", id);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        ReflectionTestUtils.setField(publicacion, "reglas", "No fumar");
        ReflectionTestUtils.setField(publicacion, "motivoBloqueo", motivoBloqueo);
        return publicacion;
    }
}
