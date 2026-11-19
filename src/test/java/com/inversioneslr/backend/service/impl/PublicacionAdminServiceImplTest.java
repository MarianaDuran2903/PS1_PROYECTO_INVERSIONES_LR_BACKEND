package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.EdicionAdministrativaRequest;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.EstadoNoEditableException;
import com.inversioneslr.backend.exception.InformacionIncompletaException;
import com.inversioneslr.backend.exception.PublicacionNoEncontradaException;
import com.inversioneslr.backend.exception.ReferenciaInvalidaException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicacionAdminServiceImplTest {

    private static final UsuarioAutenticado ADMIN_AUTENTICADO =
            new UsuarioAutenticado(1L, "admin@correo.com", Rol.ADMINISTRADOR, Instant.parse("2026-11-03T13:00:00Z"));
    private static final String MOTIVO = "Corrección de la dirección reportada por el Anfitrión";

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
    private Usuario anfitrion;
    private Usuario admin;
    private Ciudad cartagena;
    private TipoAlojamiento apartamento;

    @BeforeEach
    void setUp() {
        servicio = new PublicacionAdminServiceImpl(publicacionRepository, ciudadRepository, tipoAlojamientoRepository,
                usuarioRepository, auditoriaService, new PublicacionMapper());
        anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null),
                "carlos@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(anfitrion, "idUsuario", 5L);
        admin = new Usuario(new NombreCompleto("Admin", null, "Sistema", null),
                "admin@correo.com", "hash", Rol.ADMINISTRADOR);
        ReflectionTestUtils.setField(admin, "idUsuario", 1L);
        cartagena = new Ciudad("Cartagena");
        ReflectionTestUtils.setField(cartagena, "idCiudad", 3L);
        apartamento = new TipoAlojamiento("Apartamento");
        ReflectionTestUtils.setField(apartamento, "idTipo", 1L);
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"BORRADOR", "PENDIENTE_REVISION", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Intervenir: guarda los cambios sin cambiar el estado ni el propietario")
    void intervenir_estadoPermitido_guardaSinCambiarEstadoNiPropietario(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado);
        prepararIntervencionExitosa(publicacion);

        PublicacionResponse respuesta = servicio.intervenir(12L, request("Reglas corregidas"), ADMIN_AUTENTICADO);

        assertThat(respuesta.titulo()).isEqualTo("Casa corregida");
        assertThat(respuesta.reglas()).isEqualTo("Reglas corregidas");
        assertThat(respuesta.estado()).isEqualTo(estado);
        assertThat(respuesta.idAnfitrion()).isEqualTo(5L);
        verify(publicacionRepository).save(publicacion);
    }

    @Test
    @DisplayName("Intervenir: se registra quién intervino y el motivo")
    void intervenir_registraAdministradorYMotivo() {
        Publicacion publicacion = publicacion(EstadoPublicacion.PAUSADA);
        prepararIntervencionExitosa(publicacion);

        servicio.intervenir(12L, new EdicionAdministrativaRequest("  " + MOTIVO + "  ", cambios("Reglas")),
                ADMIN_AUTENTICADO);

        verify(auditoriaService).registrarExito(admin, AccionAuditoria.INTERVENCION_ADMIN_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION, "12", MOTIVO);
    }

    @Test
    @DisplayName("Intervenir: una publicación activa debe pausarse antes y no cambia nada")
    void intervenir_publicacionActiva_seRechazaSinCambios() {
        Publicacion publicacion = publicacion(EstadoPublicacion.ACTIVA);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        EdicionAdministrativaRequest request = request("Reglas");

        assertThatThrownBy(() -> servicio.intervenir(12L, request, ADMIN_AUTENTICADO))
                .isInstanceOf(EstadoNoEditableException.class)
                .hasMessage(PublicacionAdminServiceImpl.MENSAJE_PUBLICACION_ACTIVA);

        assertThat(publicacion.getTitulo()).isEqualTo("Apartamento frente al mar");
        verify(publicacionRepository, never()).save(any());
        verifyNoInteractions(ciudadRepository);
        verify(auditoriaService).registrarFallo("admin@correo.com", AccionAuditoria.INTERVENCION_ADMIN_PUBLICACION,
                PublicacionAdminServiceImpl.ENTIDAD_PUBLICACION, PublicacionAdminServiceImpl.MOTIVO_PUBLICACION_ACTIVA);
    }

    @Test
    @DisplayName("Intervenir: una publicación en revisión no puede quedar sin reglas")
    void intervenir_enRevisionSinReglas_seRechazaSinCambios() {
        Publicacion publicacion = publicacion(EstadoPublicacion.PENDIENTE_REVISION);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        EdicionAdministrativaRequest request = request("   ");

        assertThatThrownBy(() -> servicio.intervenir(12L, request, ADMIN_AUTENTICADO))
                .isInstanceOf(InformacionIncompletaException.class)
                .hasMessage(PublicacionAdminServiceImpl.MENSAJE_REGLAS_OBLIGATORIAS)
                .extracting("campo").isEqualTo("cambios.reglas");

        assertThat(publicacion.getReglas()).isEqualTo("Reglas originales");
        verify(publicacionRepository, never()).save(any());
    }

    @Test
    void intervenir_borradorSinReglas_seGuardaConReglasNulas() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR);
        prepararIntervencionExitosa(publicacion);

        PublicacionResponse respuesta = servicio.intervenir(12L, request(null), ADMIN_AUTENTICADO);

        assertThat(respuesta.reglas()).isNull();
    }

    @Test
    @DisplayName("Intervenir: con una ciudad inexistente la publicación conserva lo que tenía")
    void intervenir_ciudadInexistente_conservaLaInformacion() {
        Publicacion publicacion = publicacion(EstadoPublicacion.PAUSADA);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(ciudadRepository.findById(3L)).thenReturn(Optional.empty());
        EdicionAdministrativaRequest request = request("Reglas");

        assertThatThrownBy(() -> servicio.intervenir(12L, request, ADMIN_AUTENTICADO))
                .isInstanceOf(ReferenciaInvalidaException.class)
                .extracting("campo").isEqualTo("cambios.idCiudad");

        assertThat(publicacion.getTitulo()).isEqualTo("Apartamento frente al mar");
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(any(), any(), any(), any(), any());
    }

    @Test
    void intervenir_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());
        EdicionAdministrativaRequest request = request("Reglas");

        assertThatThrownBy(() -> servicio.intervenir(99L, request, ADMIN_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);
    }

    @Test
    void obtener_devuelvePublicacionDeCualquierAnfitrion() {
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion(EstadoPublicacion.ACTIVA)));

        PublicacionResponse respuesta = servicio.obtener(12L);

        assertThat(respuesta.idPublicacion()).isEqualTo(12L);
        assertThat(respuesta.idAnfitrion()).isEqualTo(5L);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void obtener_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtener(99L)).isInstanceOf(PublicacionNoEncontradaException.class);
    }

    private void prepararIntervencionExitosa(Publicacion publicacion) {
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(ciudadRepository.findById(3L)).thenReturn(Optional.of(cartagena));
        when(tipoAlojamientoRepository.findById(1L)).thenReturn(Optional.of(apartamento));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);
        when(usuarioRepository.getReferenceById(1L)).thenReturn(admin);
    }

    private Publicacion publicacion(EstadoPublicacion estado) {
        PoliticaCancelacion flexible = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(flexible, "idPolitica", 1L);
        ReflectionTestUtils.setField(flexible, "nombre", "Flexible");
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", null, 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(anfitrion, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        ReflectionTestUtils.setField(publicacion, "reglas", "Reglas originales");
        return publicacion;
    }

    private static EdicionAdministrativaRequest request(String reglas) {
        return new EdicionAdministrativaRequest(MOTIVO, cambios(reglas));
    }

    private static ActualizarPublicacionRequest cambios(String reglas) {
        return new ActualizarPublicacionRequest("Casa corregida", "Tres habitaciones y patio", "Carrera 7 # 10-15",
                3L, null, 1L, 6, new BigDecimal("320000.00"), new BigDecimal("40000.00"), reglas);
    }
}
