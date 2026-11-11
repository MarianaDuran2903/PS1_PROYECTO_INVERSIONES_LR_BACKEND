package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.RechazoPublicacionRequest;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.InformacionMinimaIncompletaException;
import com.inversioneslr.backend.exception.PublicacionNoEncontradaException;
import com.inversioneslr.backend.exception.TransicionNoPermitidaException;
import com.inversioneslr.backend.mapper.PublicacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.AccionAuditoria;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevisionPublicacionServiceImplTest {

    private static final UsuarioAutenticado ADMINISTRADOR_AUTENTICADO =
            new UsuarioAutenticado(1L, "admin@correo.com", Rol.ADMINISTRADOR, Instant.parse("2026-11-01T13:00:00Z"));

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
    private Usuario administrador;
    private Usuario anfitrion;
    private Ciudad cartagena;
    private TipoAlojamiento apartamento;
    private PoliticaCancelacion flexible;

    @BeforeEach
    void setUp() {
        servicio = new PublicacionAdminServiceImpl(publicacionRepository, ciudadRepository,
                tipoAlojamientoRepository, usuarioRepository, auditoriaService, new PublicacionMapper());
        administrador = new Usuario(new NombreCompleto("Admin", null, "Sistema", null),
                "admin@correo.com", "hash", Rol.ADMINISTRADOR);
        ReflectionTestUtils.setField(administrador, "idUsuario", 1L);
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
    @DisplayName("Criterio 2: aprobar una pendiente completa la deja ACTIVA")
    void aprobar_pendienteCompleta_quedaActiva() {
        Publicacion publicacion = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        PublicacionResponse respuesta = servicio.aprobar(12L, ADMINISTRADOR_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.ACTIVA);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.ACTIVA);
        verify(publicacionRepository).save(publicacion);
    }

    @Test
    @DisplayName("Criterio 2: la decisión queda registrada con el Administrador y la transición")
    void aprobar_exitosa_registraLaDecision() {
        Publicacion publicacion = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        servicio.aprobar(12L, ADMINISTRADOR_AUTENTICADO);

        verify(auditoriaService).registrarExito(administrador, AccionAuditoria.APROBAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION, "12", "PENDIENTE_REVISION -> ACTIVA");
        verify(auditoriaService, never()).registrarFallo(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Aprobar sin la información mínima informa todos los pendientes y no cambia el estado")
    void aprobar_informacionIncompleta_informaLosPendientesYNoCambia() {
        Publicacion publicacion = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, null, null);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.aprobar(12L, ADMINISTRADOR_AUTENTICADO))
                .isInstanceOfSatisfying(InformacionMinimaIncompletaException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo(PublicacionAdminServiceImpl.MENSAJE_INFORMACION_INCOMPLETA);
                    assertThat(ex.getPendientes()).containsOnlyKeys("barrioZona", "reglas");
                });

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PENDIENTE_REVISION);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.APROBAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION,
                PublicacionAdminServiceImpl.MOTIVO_INFORMACION_INCOMPLETA);
        verify(auditoriaService, never()).registrarExito(any(), any(), any(), any(), any());
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Aprobar fuera de PENDIENTE_REVISION se rechaza sin cambiar el estado")
    void aprobar_estadoDistintoDePendiente_seRechaza(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(12L, estado, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.aprobar(12L, ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(TransicionNoPermitidaException.class);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.APROBAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION,
                PublicacionAdminServiceImpl.MOTIVO_ESTADO_NO_PERMITIDO);
    }

    @Test
    void aprobar_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.aprobar(99L, ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);

        verify(publicacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterio 3: rechazar una pendiente la devuelve a BORRADOR")
    void rechazar_pendiente_vuelveABorrador() {
        Publicacion publicacion = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        PublicacionResponse respuesta = servicio.rechazar(12L,
                new RechazoPublicacionRequest("Las fotos no coinciden con la descripción"),
                ADMINISTRADOR_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
        verify(publicacionRepository).save(publicacion);
    }

    @Test
    @DisplayName("Criterio 3: el motivo queda registrado sin los espacios sobrantes")
    void rechazar_exitoso_registraElMotivo() {
        Publicacion publicacion = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        servicio.rechazar(12L, new RechazoPublicacionRequest("  Las fotos no coinciden con la descripción  "),
                ADMINISTRADOR_AUTENTICADO);

        verify(auditoriaService).registrarExito(administrador, AccionAuditoria.RECHAZAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION, "12",
                "Las fotos no coinciden con la descripción");
    }

    @Test
    @DisplayName("Rechazar no exige la información mínima: justamente puede ser el motivo del rechazo")
    void rechazar_pendienteSinInformacionMinima_tambienSeRechaza() {
        Publicacion publicacion = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, null, null);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        PublicacionResponse respuesta = servicio.rechazar(12L,
                new RechazoPublicacionRequest("Faltan el barrio y las reglas de la casa"),
                ADMINISTRADOR_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Rechazar fuera de PENDIENTE_REVISION se rechaza sin cambiar el estado")
    void rechazar_estadoDistintoDePendiente_seRechaza(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(12L, estado, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.rechazar(12L,
                new RechazoPublicacionRequest("Las fotos no coinciden con la descripción"),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(TransicionNoPermitidaException.class);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.RECHAZAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION,
                PublicacionAdminServiceImpl.MOTIVO_ESTADO_NO_PERMITIDO);
    }

    @Test
    void rechazar_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.rechazar(99L,
                new RechazoPublicacionRequest("Las fotos no coinciden con la descripción"),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);

        verify(publicacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Criterio 1: consulta las pendientes de la más antigua a la más reciente")
    void listarPendientes_consultaLasPendientesEnOrdenDeLlegada() {
        Publicacion primera = publicacion(11L, EstadoPublicacion.PENDIENTE_REVISION, "Bocagrande", "No fumar");
        Publicacion segunda = publicacion(12L, EstadoPublicacion.PENDIENTE_REVISION, "Manga", "No fiestas");
        Page<Publicacion> pagina = new PageImpl<>(List.of(primera, segunda), PageRequest.of(0, 20), 2);
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.PENDIENTE_REVISION), any(Pageable.class)))
                .thenReturn(pagina);

        PaginaResponse<PublicacionResponse> respuesta = servicio.listarPendientes(ADMINISTRADOR_AUTENTICADO, 0, 20);

        verify(publicacionRepository).findByEstado(eq(EstadoPublicacion.PENDIENTE_REVISION), paginaPedida.capture());
        assertThat(paginaPedida.getValue().getPageNumber()).isZero();
        assertThat(paginaPedida.getValue().getPageSize()).isEqualTo(20);
        assertThat(paginaPedida.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "idPublicacion"));
        assertThat(respuesta.contenido()).extracting(PublicacionResponse::idPublicacion).containsExactly(11L, 12L);
        assertThat(respuesta.totalElementos()).isEqualTo(2);
        assertThat(respuesta.mensaje()).isNull();
    }

    @Test
    @DisplayName("Criterio 1: sin pendientes la lista viene vacía y el mensaje lo informa")
    void listarPendientes_sinResultados_informaElMensaje() {
        Page<Publicacion> vacia = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(publicacionRepository.findByEstado(eq(EstadoPublicacion.PENDIENTE_REVISION), any(Pageable.class)))
                .thenReturn(vacia);

        PaginaResponse<PublicacionResponse> respuesta = servicio.listarPendientes(ADMINISTRADOR_AUTENTICADO, 0, 20);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.totalElementos()).isZero();
        assertThat(respuesta.mensaje()).isEqualTo(PublicacionAdminServiceImpl.MENSAJE_SIN_PENDIENTES);
    }

    private Publicacion publicacion(Long id, EstadoPublicacion estado, String barrio, String reglas) {
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "x", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", id);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        ReflectionTestUtils.setField(publicacion, "barrioZona", barrio);
        ReflectionTestUtils.setField(publicacion, "reglas", reglas);
        return publicacion;
    }
}
