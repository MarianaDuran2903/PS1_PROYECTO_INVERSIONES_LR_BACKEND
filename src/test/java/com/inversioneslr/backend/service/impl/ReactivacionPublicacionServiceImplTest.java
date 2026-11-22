package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ReactivacionPublicacionRequest;
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
class ReactivacionPublicacionServiceImplTest {

    private static final String MOTIVO_BLOQUEO = "Las fotos pertenecen a otro alojamiento";
    private static final String MOTIVO = "El Anfitrión corrigió las fotos y la dirección, y se verificaron";
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

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PAUSADA", "BLOQUEADA"})
    @DisplayName("Criterios 5 y 6: el Administrador reactiva una publicación pausada o bloqueada")
    void reactivar_pausadaOBloqueada_quedaActiva(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        PublicacionResponse respuesta = servicio.reactivar(12L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.ACTIVA);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.ACTIVA);
        verify(publicacionRepository).save(publicacion);
    }

    @Test
    @DisplayName("Criterio 6: al reactivar una bloqueada el motivo del bloqueo se limpia")
    void reactivar_bloqueada_limpiaElMotivoDelBloqueo() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BLOQUEADA, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        PublicacionResponse respuesta = servicio.reactivar(12L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO);

        assertThat(publicacion.getMotivoBloqueo()).isNull();
        assertThat(respuesta.motivoBloqueo()).isNull();
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PAUSADA", "BLOQUEADA"})
    @DisplayName("La reactivación queda registrada con el estado de origen y el motivo sin espacios sobrantes")
    void reactivar_exitosa_registraOrigenYMotivo(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(administrador);

        servicio.reactivar(12L, new ReactivacionPublicacionRequest("  " + MOTIVO + "  "), ADMINISTRADOR_AUTENTICADO);

        verify(auditoriaService).registrarExito(administrador, AccionAuditoria.REACTIVAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION, "12", estado + " -> ACTIVA: " + MOTIVO);
        verify(auditoriaService, never()).registrarFallo(any(), any(), any(), any());
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PAUSADA", "BLOQUEADA"})
    @DisplayName("Sin la información mínima informa todos los pendientes y no cambia el estado")
    void reactivar_informacionIncompleta_informaLosPendientesYNoCambia(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado, null, null);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.reactivar(12L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOfSatisfying(InformacionMinimaIncompletaException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo(PublicacionAdminServiceImpl.MENSAJE_INFORMACION_INCOMPLETA);
                    assertThat(ex.getPendientes()).containsOnlyKeys("barrioZona", "reglas");
                });

        assertThat(publicacion.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.REACTIVAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION,
                PublicacionAdminServiceImpl.MOTIVO_INFORMACION_INCOMPLETA);
        verify(auditoriaService, never()).registrarExito(any(Usuario.class), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Si la reactivación falla, la bloqueada conserva el motivo del bloqueo")
    void reactivar_informacionIncompleta_conservaElMotivoDelBloqueo() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BLOQUEADA, null, "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.reactivar(12L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(InformacionMinimaIncompletaException.class);

        assertThat(publicacion.getMotivoBloqueo()).isEqualTo(MOTIVO_BLOQUEO);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "PENDIENTE_REVISION", "ACTIVA"})
    @DisplayName("Fuera de PAUSADA y BLOQUEADA se rechaza sin cambiar el estado")
    void reactivar_otroEstado_seRechaza(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.reactivar(12L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(TransicionNoPermitidaException.class);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.REACTIVAR_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION,
                PublicacionAdminServiceImpl.MOTIVO_ESTADO_NO_PERMITIDO);
        verify(auditoriaService, never()).registrarExito(any(Usuario.class), any(), any(), any(), any());
    }

    @Test
    void reactivar_yaActiva_explicaQueYaEstaActiva() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacion(EstadoPublicacion.ACTIVA, "Bocagrande", "No fumar")));

        assertThatThrownBy(() -> servicio.reactivar(12L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .hasMessage("La publicación ya está activa");
    }

    @Test
    void reactivar_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reactivar(99L, new ReactivacionPublicacionRequest(MOTIVO),
                ADMINISTRADOR_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);

        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService, never()).registrarFallo(any(), any(), any(), any());
    }

    private Publicacion publicacion(EstadoPublicacion estado, String barrio, String reglas) {
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "x", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        ReflectionTestUtils.setField(publicacion, "barrioZona", barrio);
        ReflectionTestUtils.setField(publicacion, "reglas", reglas);
        if (estado == EstadoPublicacion.BLOQUEADA) {
            ReflectionTestUtils.setField(publicacion, "motivoBloqueo", MOTIVO_BLOQUEO);
        }
        return publicacion;
    }
}
