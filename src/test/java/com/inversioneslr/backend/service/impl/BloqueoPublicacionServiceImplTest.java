package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.BloqueoPublicacionRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloqueoPublicacionServiceImplTest {

    private static final String MOTIVO = "Las fotos pertenecen a otro alojamiento y la dirección no existe";
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
    @DisplayName("Criterio 1: el Administrador bloquea una publicación ACTIVA")
    void bloquear_activa_quedaBloqueada() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        PublicacionResponse respuesta = servicio.bloquear(12L, new BloqueoPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.BLOQUEADA);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BLOQUEADA);
        verify(publicacionRepository).save(publicacion);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PENDIENTE_REVISION", "ACTIVA", "PAUSADA"})
    @DisplayName("Criterio 1: también se bloquea una publicación en revisión o pausada")
    void bloquear_estadoPermitido_quedaBloqueada(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        servicio.bloquear(12L, new BloqueoPublicacionRequest(MOTIVO), ADMINISTRADOR_AUTENTICADO);

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BLOQUEADA);
    }

    @Test
    @DisplayName("Criterio 2: el motivo queda registrado con el Administrador y sin espacios sobrantes")
    void bloquear_exitoso_registraElMotivo() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        servicio.bloquear(12L, new BloqueoPublicacionRequest("  " + MOTIVO + "  "), ADMINISTRADOR_AUTENTICADO);

        assertThat(publicacion.getMotivoBloqueo()).isEqualTo(MOTIVO);
        verify(auditoriaService).registrarExito(administrador, AccionAuditoria.BLOQUEAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION, "12", MOTIVO);
        verify(auditoriaService, never()).registrarFallo(any(), any(), any(), any());
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "BLOQUEADA"})
    @DisplayName("Un borrador o una publicación ya bloqueada se rechaza sin cambiar el estado")
    void bloquear_estadoNoPermitido_seRechaza(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.bloquear(12L, new BloqueoPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(TransicionNoPermitidaException.class);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.BLOQUEAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION,
                PublicacionAdminServiceImpl.MOTIVO_ESTADO_NO_PERMITIDO);
        verify(auditoriaService, never()).registrarExito(any(Usuario.class), any(), any(), any(), any());
    }

    @Test
    void bloquear_yaBloqueada_explicaQueYaEstaBloqueada() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacion(EstadoPublicacion.BLOQUEADA)));

        assertThatThrownBy(() -> servicio.bloquear(12L, new BloqueoPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .hasMessage("La publicación ya está bloqueada");
    }

    @Test
    void bloquear_borrador_explicaQueNoHayNadaQueBloquear() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacion(EstadoPublicacion.BORRADOR)));

        assertThatThrownBy(() -> servicio.bloquear(12L, new BloqueoPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .hasMessage("La publicación está en borrador: aún no se ha publicado, no hay nada que bloquear");
    }

    @Test
    void bloquear_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.bloquear(99L, new BloqueoPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);

        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService, never()).registrarFallo(any(), any(), any(), any());
    }

    private Publicacion publicacion(EstadoPublicacion estado) {
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "Bocagrande", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        ReflectionTestUtils.setField(publicacion, "reglas", "No fumar");
        return publicacion;
    }
}
