package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.ActualizarPublicacionRequest;
import com.inversioneslr.backend.dto.request.CrearPublicacionRequest;
import com.inversioneslr.backend.dto.response.PaginaResponse;
import com.inversioneslr.backend.dto.response.PublicacionResponse;
import com.inversioneslr.backend.dto.response.PublicacionResumenResponse;
import com.inversioneslr.backend.exception.EstadoNoEditableException;
import com.inversioneslr.backend.exception.PublicacionAjenaException;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    @Test
    @DisplayName("Editar: el propietario guarda los cambios válidos y el estado no cambia")
    void actualizar_propietario_guardaCambios() {
        Publicacion existente = publicacionExistente(EstadoPublicacion.BORRADOR);
        prepararEdicionExitosa(existente);

        PublicacionResponse respuesta = servicio.actualizar(12L,
                actualizacion(3L, "  Casa renovada  ", " Sin fiestas después de las 10 pm "), ANFITRION_AUTENTICADO);

        assertThat(respuesta.titulo()).isEqualTo("Casa renovada");
        assertThat(respuesta.capacidad()).isEqualTo(6);
        assertThat(respuesta.precioNoche()).isEqualByComparingTo("320000.00");
        assertThat(respuesta.tarifaLimpieza()).isEqualByComparingTo("40000.00");
        assertThat(respuesta.reglas()).isEqualTo("Sin fiestas después de las 10 pm");
        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.BORRADOR);
        assertThat(respuesta.idAnfitrion()).isEqualTo(5L);
        verify(publicacionRepository).save(existente);
    }

    @Test
    @DisplayName("Editar: el cambio queda registrado para trazabilidad")
    void actualizar_registraEventoDeAuditoria() {
        Publicacion existente = publicacionExistente(EstadoPublicacion.BORRADOR);
        prepararEdicionExitosa(existente);

        servicio.actualizar(12L, actualizacion(3L, "Casa renovada", null), ANFITRION_AUTENTICADO);

        verify(auditoriaService).registrarExito(anfitrion, AccionAuditoria.EDITAR_PUBLICACION,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, "12");
    }

    @Test
    void actualizar_publicacionPausada_seEditaYSigueEnPausada() {
        Publicacion existente = publicacionExistente(EstadoPublicacion.PAUSADA);
        prepararEdicionExitosa(existente);

        PublicacionResponse respuesta = servicio.actualizar(12L, actualizacion(3L, "Casa renovada", null),
                ANFITRION_AUTENTICADO);

        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.PAUSADA);
    }

    @Test
    void actualizar_reglasEnBlanco_quedanNulas() {
        Publicacion existente = publicacionExistente(EstadoPublicacion.BORRADOR);
        prepararEdicionExitosa(existente);

        PublicacionResponse respuesta = servicio.actualizar(12L, actualizacion(3L, "Casa renovada", "   "),
                ANFITRION_AUTENTICADO);

        assertThat(respuesta.reglas()).isNull();
    }

    @Test
    @DisplayName("Editar: una publicación de otro Anfitrión se deniega y el intento queda registrado")
    void actualizar_publicacionAjena_seDeniegaYRegistraIntento() {
        Publicacion ajena = publicacionDe(otroAnfitrion(), EstadoPublicacion.BORRADOR);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(ajena));
        ActualizarPublicacionRequest request = actualizacion(3L, "Casa renovada", null);

        assertThatThrownBy(() -> servicio.actualizar(12L, request, ANFITRION_AUTENTICADO))
                .isInstanceOf(PublicacionAjenaException.class);

        assertThat(ajena.getTitulo()).isEqualTo("Apartamento frente al mar");
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService).registrarIntentoFallido(5L, "carlos@correo.com", AccionAuditoria.ACCESO_DENEGADO,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, "12", PublicacionServiceImpl.MOTIVO_NO_ES_PROPIETARIO);
    }

    @Test
    void actualizar_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());
        ActualizarPublicacionRequest request = actualizacion(3L, "Casa renovada", null);

        assertThatThrownBy(() -> servicio.actualizar(99L, request, ANFITRION_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class)
                .hasMessage("La publicación no existe");

        verify(publicacionRepository, never()).save(any());
    }

    @ParameterizedTest(name = "estado {0}")
    @EnumSource(value = EstadoPublicacion.class, names = {"ACTIVA", "PENDIENTE_REVISION", "BLOQUEADA"})
    @DisplayName("Editar: fuera de BORRADOR o PAUSADA se rechaza sin cambiar nada")
    void actualizar_estadoNoEditable_seRechazaSinCambios(EstadoPublicacion estado) {
        Publicacion existente = publicacionExistente(estado);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(existente));
        ActualizarPublicacionRequest request = actualizacion(3L, "Casa renovada", null);

        assertThatThrownBy(() -> servicio.actualizar(12L, request, ANFITRION_AUTENTICADO))
                .isInstanceOf(EstadoNoEditableException.class);

        assertThat(existente.getTitulo()).isEqualTo("Apartamento frente al mar");
        assertThat(existente.getEstado()).isEqualTo(estado);
        verify(publicacionRepository, never()).save(any());
        verifyNoInteractions(ciudadRepository);
        verify(auditoriaService).registrarFallo("carlos@correo.com", AccionAuditoria.EDITAR_PUBLICACION,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, PublicacionServiceImpl.MOTIVO_ESTADO_NO_EDITABLE);
    }

    @Test
    void actualizar_publicacionActiva_pideQueSePause() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacionExistente(EstadoPublicacion.ACTIVA)));
        ActualizarPublicacionRequest request = actualizacion(3L, "Casa renovada", null);

        assertThatThrownBy(() -> servicio.actualizar(12L, request, ANFITRION_AUTENTICADO))
                .hasMessage("La publicación está activa: solicite pausarla para editarla");
    }

    @Test
    @DisplayName("Editar: con una ciudad inexistente la publicación conserva lo que tenía")
    void actualizar_ciudadInexistente_conservaLaInformacionPrevia() {
        Publicacion existente = publicacionExistente(EstadoPublicacion.BORRADOR);
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(existente));
        when(ciudadRepository.findById(77L)).thenReturn(Optional.empty());
        ActualizarPublicacionRequest request = actualizacion(77L, "Casa renovada", null);

        assertThatThrownBy(() -> servicio.actualizar(12L, request, ANFITRION_AUTENTICADO))
                .isInstanceOf(ReferenciaInvalidaException.class)
                .extracting("campo").isEqualTo("idCiudad");

        assertThat(existente.getTitulo()).isEqualTo("Apartamento frente al mar");
        assertThat(existente.getCiudad()).isSameAs(cartagena);
        verify(publicacionRepository, never()).save(any());
        verify(auditoriaService, never()).registrarExito(any(), any(), any(), any());
    }

    @Test
    void obtenerPropia_propietario_devuelveLaPublicacion() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacionExistente(EstadoPublicacion.ACTIVA)));

        PublicacionResponse respuesta = servicio.obtenerPropia(12L, ANFITRION_AUTENTICADO);

        assertThat(respuesta.idPublicacion()).isEqualTo(12L);
        assertThat(respuesta.estado()).isEqualTo(EstadoPublicacion.ACTIVA);
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void obtenerPropia_publicacionAjena_seDeniegaYRegistraIntento() {
        when(publicacionRepository.findById(12L))
                .thenReturn(Optional.of(publicacionDe(otroAnfitrion(), EstadoPublicacion.BORRADOR)));

        assertThatThrownBy(() -> servicio.obtenerPropia(12L, ANFITRION_AUTENTICADO))
                .isInstanceOf(PublicacionAjenaException.class);

        verify(auditoriaService).registrarIntentoFallido(5L, "carlos@correo.com", AccionAuditoria.ACCESO_DENEGADO,
                PublicacionServiceImpl.ENTIDAD_PUBLICACION, "12", PublicacionServiceImpl.MOTIVO_NO_ES_PROPIETARIO);
    }

    @Test
    void obtenerPropia_publicacionInexistente_lanzaNoEncontrada() {
        when(publicacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.obtenerPropia(99L, ANFITRION_AUTENTICADO))
                .isInstanceOf(PublicacionNoEncontradaException.class);
    }

    @Test
    @DisplayName("Listar: se consultan solo las publicaciones del Anfitrión del token, las más recientes primero")
    void listarPropias_filtraPorElAnfitrionDelToken() {
        when(publicacionRepository.findByAnfitrionIdUsuario(eq(5L), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(publicacionExistente(EstadoPublicacion.PAUSADA)),
                        inv.getArgument(1), 1));

        PaginaResponse<PublicacionResumenResponse> respuesta = servicio.listarPropias(ANFITRION_AUTENTICADO, 2, 10);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(publicacionRepository).findByAnfitrionIdUsuario(eq(5L), captor.capture());
        Pageable pedido = captor.getValue();
        assertThat(pedido.getPageNumber()).isEqualTo(2);
        assertThat(pedido.getPageSize()).isEqualTo(10);
        assertThat(pedido.getSort().getOrderFor("idPublicacion")).isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.DESC);
        assertThat(respuesta.contenido()).singleElement().satisfies(resumen -> {
            assertThat(resumen.idPublicacion()).isEqualTo(12L);
            assertThat(resumen.estado()).isEqualTo(EstadoPublicacion.PAUSADA);
            assertThat(resumen.ciudad().nombre()).isEqualTo("Cartagena");
            assertThat(resumen.tipo().nombre()).isEqualTo("Apartamento");
        });
        assertThat(respuesta.mensaje()).isNull();
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @DisplayName("Listar: sin publicaciones la página viene vacía e informa que no hay ninguna")
    void listarPropias_sinPublicaciones_informaQueNoHay() {
        when(publicacionRepository.findByAnfitrionIdUsuario(eq(5L), any(Pageable.class)))
                .thenAnswer(inv -> Page.empty(inv.getArgument(1)));

        PaginaResponse<PublicacionResumenResponse> respuesta = servicio.listarPropias(ANFITRION_AUTENTICADO, 0, 20);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.totalElementos()).isZero();
        assertThat(respuesta.mensaje()).isEqualTo("No tiene publicaciones registradas");
    }

    @Test
    void listarPropias_paginaPosteriorALaUltima_noMuestraElMensaje() {
        when(publicacionRepository.findByAnfitrionIdUsuario(eq(5L), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(), inv.getArgument(1), 3));

        PaginaResponse<PublicacionResumenResponse> respuesta = servicio.listarPropias(ANFITRION_AUTENTICADO, 5, 20);

        assertThat(respuesta.contenido()).isEmpty();
        assertThat(respuesta.totalElementos()).isEqualTo(3);
        assertThat(respuesta.mensaje()).isNull();
    }

    private void prepararEdicionExitosa(Publicacion existente) {
        when(publicacionRepository.findById(12L)).thenReturn(Optional.of(existente));
        when(ciudadRepository.findById(3L)).thenReturn(Optional.of(cartagena));
        when(tipoAlojamientoRepository.findById(1L)).thenReturn(Optional.of(apartamento));
        when(publicacionRepository.save(existente)).thenReturn(existente);
    }

    private Publicacion publicacionExistente(EstadoPublicacion estado) {
        return publicacionDe(anfitrion, estado);
    }

    private Publicacion publicacionDe(Usuario propietario, EstadoPublicacion estado) {
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", null, 4, new BigDecimal("250000.00"));
        Publicacion publicacion = new Publicacion(propietario, flexible, datos, cartagena, apartamento);
        ReflectionTestUtils.setField(publicacion, "idPublicacion", 12L);
        ReflectionTestUtils.setField(publicacion, "estado", estado);
        return publicacion;
    }

    private static Usuario otroAnfitrion() {
        Usuario otro = new Usuario(new NombreCompleto("Ana", null, "Ruiz", null),
                "ana@correo.com", "hash", Rol.ANFITRION);
        ReflectionTestUtils.setField(otro, "idUsuario", 8L);
        return otro;
    }

    private static ActualizarPublicacionRequest actualizacion(Long idCiudad, String titulo, String reglas) {
        return new ActualizarPublicacionRequest(titulo, "Tres habitaciones y patio", "Carrera 7 # 10-15", idCiudad,
                "Manga", 1L, 6, new BigDecimal("320000.00"), new BigDecimal("40000.00"), reglas);
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
