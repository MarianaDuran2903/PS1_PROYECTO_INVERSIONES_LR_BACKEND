package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.exception.InformacionMinimaIncompletaException;
import com.inversioneslr.backend.exception.PublicacionAjenaException;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitudActivacionServiceImplTest {

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
        flexible = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(flexible, "idPolitica", 1L);
        ReflectionTestUtils.setField(flexible, "nombre", "Flexible");
    }

    @Test
    @DisplayName("Criterios 1 y 2: con la información mínima completa pasa a PENDIENTE_REVISION")
    void solicitarActivacion_borradorCompleto_pasaARevision() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);

        PublicacionResponse respuesta = servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.PENDIENTE_REVISION);
        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.PENDIENTE_REVISION);
        verify(publicacionRepository).save(publicacion);
    }

    @Test
    @DisplayName("Criterio 4: la transición queda registrada con el estado anterior y el nuevo")
    void solicitarActivacion_exitosa_registraEventoConDetalle() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));
        when(publicacionRepository.save(publicacion)).thenReturn(publicacion);

        servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO);

        verify(auditoriaService).registrarExito(anfitrion, AccionAuditoria.ENVIAR_A_REVISION,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, "12", "BORRADOR -> PENDIENTE_REVISION");
    }

    @Test
    @DisplayName("Criterio 3: informa todos los campos pendientes y conserva el estado")
    void solicitarActivacion_variosFaltantes_losInformaTodosYNoCambiaElEstado() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR, null, null);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO))
                .isInstanceOfSatisfying(InformacionMinimaIncompletaException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("La publicación no está lista para revisión");
                    assertThat(ex.getPendientes()).containsOnlyKeys("barrioZona", "reglas");
                });

        assertThat(publicacion.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("carlos@correo.com", AccionAuditoria.ENVIAR_A_REVISION,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, PublicacionServiceImpl.MOTIVO_INFORMACION_INCOMPLETA);
    }

    @Test
    @DisplayName("Criterio 3: reglas o barrio solo con espacios cuentan como faltantes")
    void solicitarActivacion_camposEnBlanco_seTratanComoFaltantes() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR, "   ", "  ");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO))
                .isInstanceOfSatisfying(InformacionMinimaIncompletaException.class,
                        ex -> assertThat(ex.getPendientes()).containsOnlyKeys("barrioZona", "reglas"));
    }

    @Test
    @DisplayName("Los cuatro campos faltantes se informan en un solo rechazo")
    void solicitarActivacion_sinNingunDato_informaLosCuatro() {
        Publicacion publicacion = publicacion(EstadoPublicacion.BORRADOR, null, null);
        ReflectionTestUtils.setField(publicacion, "descripcion", null);
        ReflectionTestUtils.setField(publicacion, "direccionTextual", " ");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO))
                .isInstanceOfSatisfying(InformacionMinimaIncompletaException.class, ex -> {
                    assertThat(List.copyOf(ex.getPendientes().keySet()))
                            .containsExactlyInAnyOrder("descripcion", "direccionTextual", "barrioZona", "reglas");
                    assertThat(ex.getPendientes().get("descripcion")).contains("descripción");
                });
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"PENDIENTE_REVISION", "ACTIVA", "PAUSADA", "BLOQUEADA"})
    @DisplayName("Fuera de BORRADOR se rechaza sin cambiar el estado")
    void solicitarActivacion_estadoDistintoDeBorrador_seRechaza(EstadoPublicacion estado) {
        Publicacion publicacion = publicacion(estado, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO))
                .isInstanceOf(TransicionNoPermitidaException.class);

        assertThat(publicacion.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarFallo("carlos@correo.com", AccionAuditoria.ENVIAR_A_REVISION,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, PublicacionServiceImpl.MOTIVO_ESTADO_NO_PERMITIDO);
    }

    @Test
    void solicitarActivacion_pausada_explicaQueLaRevisaElAdministrador() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacion(EstadoPublicacion.PAUSADA, "Bocagrande", "No fumar")));

        assertThatThrownBy(() -> servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO))
                .hasMessage("La publicación está pausada: el Administrador gestiona su revisión");
    }

    @Test
    void solicitarActivacion_publicacionAjena_seDeniegaYRegistraIntento() {
        Usuario otro = new Usuario(new NombreCompleto("Ana", null, "Ruiz", null),
                "ana@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(otro, "idUsuario", 8L);
        Publicacion ajena = publicacionDe(otro, EstadoPublicacion.BORRADOR, "Bocagrande", "No fumar");
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> servicio.solicitarActivacion(12L, ANFITRION_AUTENTICADO))
                .isInstanceOf(PublicacionAjenaException.class);

        assertThat(ajena.getEstado()).isEqualTo(EstadoPublicacion.BORRADOR);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarIntentoFallido(5L, "carlos@correo.com", AccionAuditoria.ACCESO_DENEGADO,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, "12", PublicacionServiceImpl.MOTIVO_NO_ES_PROPIETARIO);
    }

    @Test
    void solicitarActivacion_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.solicitarActivacion(99L, ANFITRION_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);

        verify(publicacionRepository, never()).save(any());
    }

    private Publicacion publicacion(EstadoPublicacion estado, String barrio, String reglas) {
        return publicacionDe(anfitrion, estado, barrio, reglas);
    }

    private Publicacion publicacionDe(Usuario propietario, EstadoPublicacion estado, String barrio, String reglas) {
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "x", 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(propietario, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        ReflectionTestUtils.setField(publicacion, "barrioZona", barrio);
        ReflectionTestUtils.setField(publicacion, "reglas", reglas);
        return publicacion;
    }
}
