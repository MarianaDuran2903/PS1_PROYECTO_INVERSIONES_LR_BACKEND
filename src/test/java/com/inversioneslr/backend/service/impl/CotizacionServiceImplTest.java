package com.inversioneslr.backend.service.impl;

import com.inversioneslr.backend.dto.request.CotizarEstanciaRequest;
import com.inversioneslr.backend.dto.response.CotizacionResponse;
import com.inversioneslr.backend.exception.AlojamientoNoDisponibleException;
import com.inversioneslr.backend.exception.CotizacionNoDisponibleException;
import com.inversioneslr.backend.exception.DatosReservaInvalidosException;
import com.inversioneslr.backend.mapper.CotizacionMapper;
import com.inversioneslr.backend.model.Ciudad;
import com.inversioneslr.backend.model.ConfiguracionPlataforma;
import com.inversioneslr.backend.model.DatosPublicacion;
import com.inversioneslr.backend.model.NombreCompleto;
import com.inversioneslr.backend.model.PeriodoDisponibilidad;
import com.inversioneslr.backend.model.PoliticaCancelacion;
import com.inversioneslr.backend.model.Publicacion;
import com.inversioneslr.backend.model.ServicioAdicional;
import com.inversioneslr.backend.model.TipoAlojamiento;
import com.inversioneslr.backend.model.Usuario;
import com.inversioneslr.backend.model.enums.EstadoPublicacion;
import com.inversioneslr.backend.model.enums.Rol;
import com.inversioneslr.backend.model.enums.TipoCobro;
import com.inversioneslr.backend.model.enums.TipoPeriodo;
import com.inversioneslr.backend.repository.ConfiguracionPlataformaRepository;
import com.inversioneslr.backend.repository.PeriodoDisponibilidadRepository;
import com.inversioneslr.backend.repository.PublicacionRepository;
import com.inversioneslr.backend.repository.ServicioAdicionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CotizacionServiceImplTest {

    /** 10:00 en Bogotá (UTC-5) del 1 de junio de 2030. */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2030-06-01T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate LLEGADA = LocalDate.parse("2030-06-10");
    private static final LocalDate SALIDA = LocalDate.parse("2030-06-13");

    @Mock
    private PublicacionRepository publicacionRepository;
    @Mock
    private PeriodoDisponibilidadRepository periodoRepository;
    @Mock
    private ConfiguracionPlataformaRepository configuracionRepository;
    @Mock
    private ServicioAdicionalRepository servicioAdicionalRepository;

    private CotizacionServiceImpl servicio;
    private Publicacion publicacion;
    private ConfiguracionPlataforma configuracion;

    @BeforeEach
    void setUp() {
        servicio = servicioCon(RELOJ);
        publicacion = publicacionActiva();
        configuracion = BeanUtils.instantiateClass(ConfiguracionPlataforma.class);
        ReflectionTestUtils.setField(configuracion, "porcentajeTarifaServicio", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(configuracion, "porcentajeDescuento", new BigDecimal("10.00"));
        ReflectionTestUtils.setField(configuracion, "nochesMinimasDescuento", 7);
    }

    private CotizacionServiceImpl servicioCon(Clock reloj) {
        return new CotizacionServiceImpl(publicacionRepository, periodoRepository, configuracionRepository,
                servicioAdicionalRepository, new CotizacionMapper(), reloj);
    }

    // ------------------------------------------------------------------ cálculo

    @Test
    @DisplayName("Cotiza noches, base, limpieza, tarifa de servicio y total de una estadía disponible")
    void cotizar_conCalendarioCompleto_devuelveElDesglose() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));

        CotizacionResponse cotizacion = servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null));

        assertThat(cotizacion.idPublicacion()).isEqualTo(12L);
        assertThat(cotizacion.fechaLlegada()).isEqualTo(LLEGADA);
        assertThat(cotizacion.fechaSalida()).isEqualTo(SALIDA);
        assertThat(cotizacion.noches()).isEqualTo(3);
        assertThat(cotizacion.precio().precioNoche()).isEqualByComparingTo("250000.00");
        assertThat(cotizacion.precio().precioBase()).isEqualByComparingTo("750000.00");
        assertThat(cotizacion.precio().porcentajeDescuento()).isEqualByComparingTo("0");
        assertThat(cotizacion.precio().valorDescuento()).isEqualByComparingTo("0");
        assertThat(cotizacion.precio().tarifaLimpieza()).isEqualByComparingTo("50000.00");
        assertThat(cotizacion.precio().porcentajeTarifaServicio()).isEqualByComparingTo("10.00");
        assertThat(cotizacion.precio().tarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(cotizacion.precio().serviciosAdicionales()).isEmpty();
        assertThat(cotizacion.precio().totalServiciosAdicionales()).isEqualByComparingTo("0");
        assertThat(cotizacion.precio().total()).isEqualByComparingTo("875000.00");
    }

    @Test
    @DisplayName("Una estadía que llega al mínimo de noches recibe el descuento configurado")
    void cotizar_conEstadiaLarga_aplicaElDescuento() {
        LocalDate salida = LocalDate.parse("2030-06-17");
        prepararCotizacion(LLEGADA, salida, List.of(libre("2030-06-01", "2030-07-01")));

        CotizacionResponse cotizacion = servicio.cotizar(solicitud(LLEGADA, salida, 2, null));

        assertThat(cotizacion.noches()).isEqualTo(7);
        assertThat(cotizacion.precio().precioBase()).isEqualByComparingTo("1750000.00");
        assertThat(cotizacion.precio().porcentajeDescuento()).isEqualByComparingTo("10.00");
        assertThat(cotizacion.precio().valorDescuento()).isEqualByComparingTo("175000.00");
        assertThat(cotizacion.precio().tarifaServicio()).isEqualByComparingTo("157500.00");
        assertThat(cotizacion.precio().total()).isEqualByComparingTo("1782500.00");
    }

    @Test
    @DisplayName("Los servicios elegidos aparecen con su valor, en el orden pedido, y se suman al total")
    void cotizar_conServicios_losIncluyeEnElOrdenPedido() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));
        when(servicioAdicionalRepository.findByIdIdPublicacionAndIdNombreIn(12L,
                List.of("Traslado al aeropuerto", "Desayuno")))
                .thenReturn(List.of(desayuno(), traslado()));

        CotizacionResponse cotizacion = servicio.cotizar(
                solicitud(LLEGADA, SALIDA, 2, List.of("Traslado al aeropuerto", "Desayuno")));

        assertThat(cotizacion.precio().serviciosAdicionales()).hasSize(2);
        assertThat(cotizacion.precio().serviciosAdicionales().get(0).nombre()).isEqualTo("Traslado al aeropuerto");
        assertThat(cotizacion.precio().serviciosAdicionales().get(0).tipoCobro()).isEqualTo(TipoCobro.POR_ESTANCIA);
        assertThat(cotizacion.precio().serviciosAdicionales().get(0).precio()).isEqualByComparingTo("80000.00");
        assertThat(cotizacion.precio().serviciosAdicionales().get(0).valor()).isEqualByComparingTo("80000.00");
        assertThat(cotizacion.precio().serviciosAdicionales().get(1).nombre()).isEqualTo("Desayuno");
        assertThat(cotizacion.precio().serviciosAdicionales().get(1).tipoCobro()).isEqualTo(TipoCobro.POR_NOCHE);
        assertThat(cotizacion.precio().serviciosAdicionales().get(1).valor()).isEqualByComparingTo("60000.00");
        assertThat(cotizacion.precio().totalServiciosAdicionales()).isEqualByComparingTo("140000.00");
        assertThat(cotizacion.precio().tarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(cotizacion.precio().total()).isEqualByComparingTo("1015000.00");
    }

    @Test
    @DisplayName("Los espacios sobrantes en el nombre de un servicio no impiden encontrarlo")
    void cotizar_conNombreConEspacios_loEncuentra() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));
        when(servicioAdicionalRepository.findByIdIdPublicacionAndIdNombreIn(12L, List.of("Desayuno")))
                .thenReturn(List.of(desayuno()));

        CotizacionResponse cotizacion = servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, List.of("  Desayuno ")));

        assertThat(cotizacion.precio().serviciosAdicionales()).hasSize(1);
        assertThat(cotizacion.precio().serviciosAdicionales().get(0).nombre()).isEqualTo("Desayuno");
    }

    @Test
    @DisplayName("Una lista vacía de servicios no consulta la base")
    void cotizar_conListaDeServiciosVacia_noConsultaServicios() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));

        CotizacionResponse cotizacion = servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, List.of()));

        assertThat(cotizacion.precio().serviciosAdicionales()).isEmpty();
        verifyNoInteractions(servicioAdicionalRepository);
    }

    @Test
    @DisplayName("Los huéspedes son opcionales y no cambian el precio")
    void cotizar_sinHuespedes_cotizaIgual() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));

        CotizacionResponse cotizacion = servicio.cotizar(solicitud(LLEGADA, SALIDA, null, null));

        assertThat(cotizacion.precio().total()).isEqualByComparingTo("875000.00");
    }

    @Test
    @DisplayName("Una estadía que cruza dos tramos contiguos se puede cotizar")
    void cotizar_cruzandoDosTramosContiguos_seCotiza() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-06-11"),
                libre("2030-06-11", "2030-07-01")));

        assertThat(servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null)).precio().total())
                .isEqualByComparingTo("875000.00");
    }

    @Test
    @DisplayName("Cotizar solo lee: no bloquea la publicación")
    void cotizar_noBloqueaLaPublicacion() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));

        servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null));

        verify(publicacionRepository, never()).bloquearPorIdYEstado(any(), any());
    }

    // ------------------------------------------------------------------ fechas

    @Test
    @DisplayName("Una llegada anterior a hoy se rechaza sin consultar nada")
    void cotizar_conLlegadaPasada_seRechazaEnElCampoDeLlegada() {
        assertThatThrownBy(() -> servicio.cotizar(solicitud(LocalDate.parse("2030-05-31"), SALIDA, 2, null)))
                .isInstanceOfSatisfying(DatosReservaInvalidosException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("fechaLlegada");
                    assertThat(error.getMessage()).contains("anterior a hoy");
                });

        verifyNoInteractions(publicacionRepository, periodoRepository, configuracionRepository);
    }

    @Test
    @DisplayName("Llegar hoy está permitido")
    void cotizar_conLlegadaHoy_seCotiza() {
        LocalDate hoy = LocalDate.parse("2030-06-01");
        LocalDate salida = LocalDate.parse("2030-06-03");
        prepararCotizacion(hoy, salida, List.of(libre("2030-06-01", "2030-07-01")));

        assertThat(servicio.cotizar(solicitud(hoy, salida, 2, null)).noches()).isEqualTo(2);
    }

    @Test
    @DisplayName("\"Hoy\" se cuenta en Bogotá: pasadas las 7 p. m. todavía se puede llegar ese día")
    void cotizar_enLaNocheDeBogota_elDiaLocalSigueSiendoHoy() {
        // 22:00 del 1 de junio en Bogotá es la 03:00 del 2 de junio en UTC.
        CotizacionServiceImpl enLaNoche = servicioCon(
                Clock.fixed(Instant.parse("2030-06-02T03:00:00Z"), ZoneOffset.UTC));
        LocalDate hoy = LocalDate.parse("2030-06-01");
        LocalDate salida = LocalDate.parse("2030-06-03");
        prepararCotizacion(hoy, salida, List.of(libre("2030-06-01", "2030-07-01")));

        assertThat(enLaNoche.cotizar(solicitud(hoy, salida, 2, null)).noches()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ publicación

    @Test
    @DisplayName("Una publicación inexistente o que no está activa no se cotiza")
    void cotizar_conPublicacionNoActiva_lanzaAlojamientoNoDisponible() {
        when(publicacionRepository.findByIdPublicacionAndEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null)))
                .isInstanceOf(AlojamientoNoDisponibleException.class);

        verifyNoInteractions(periodoRepository, servicioAdicionalRepository, configuracionRepository);
    }

    @Test
    @DisplayName("Más huéspedes que la capacidad se rechaza en el campo de huéspedes")
    void cotizar_conMasHuespedesQueLaCapacidad_seRechaza() {
        publicacionEncontrada();

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 5, null)))
                .isInstanceOfSatisfying(DatosReservaInvalidosException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("huespedes");
                    assertThat(error.getMessage()).contains("4");
                });

        verifyNoInteractions(periodoRepository);
    }

    @Test
    @DisplayName("Los huéspedes en el límite de la capacidad se aceptan")
    void cotizar_conLaCapacidadExacta_seCotiza() {
        prepararCotizacion(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));

        assertThat(servicio.cotizar(solicitud(LLEGADA, SALIDA, 4, null)).noches()).isEqualTo(3);
    }

    // ------------------------------------------------------------------ servicios adicionales

    @Test
    @DisplayName("Un servicio repetido se rechaza sin consultar la base, aunque difiera en espacios")
    void cotizar_conServicioRepetido_seRechaza() {
        publicacionEncontrada();

        assertThatThrownBy(() -> servicio.cotizar(
                solicitud(LLEGADA, SALIDA, 2, List.of("Desayuno", " Desayuno"))))
                .isInstanceOfSatisfying(DatosReservaInvalidosException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("serviciosAdicionales");
                    assertThat(error.getMessage()).contains("repita");
                });

        verifyNoInteractions(servicioAdicionalRepository, periodoRepository);
    }

    @Test
    @DisplayName("Un servicio que la publicación no ofrece se rechaza nombrándolo")
    void cotizar_conServicioInexistente_seRechazaNombrandolo() {
        publicacionEncontrada();
        when(servicioAdicionalRepository.findByIdIdPublicacionAndIdNombreIn(12L, List.of("Desayuno", "Spa")))
                .thenReturn(List.of(desayuno()));

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, List.of("Desayuno", "Spa"))))
                .isInstanceOfSatisfying(DatosReservaInvalidosException.class, error -> {
                    assertThat(error.getCampo()).isEqualTo("serviciosAdicionales");
                    assertThat(error.getMessage()).contains("Spa");
                });

        verifyNoInteractions(periodoRepository);
    }

    // ------------------------------------------------------------------ disponibilidad

    @Test
    @DisplayName("Sin calendario para las noches pedidas no se genera la cotización")
    void cotizar_sinTramosLibres_lanzaCotizacionNoDisponible() {
        publicacionEncontrada();
        tramosLibres(LLEGADA, SALIDA, List.of());

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null)))
                .isInstanceOf(CotizacionNoDisponibleException.class)
                .hasMessageContaining("cotización");

        verifyNoInteractions(configuracionRepository);
    }

    @Test
    @DisplayName("Un hueco en el calendario impide cotizar")
    void cotizar_conHuecoEnElCalendario_lanzaCotizacionNoDisponible() {
        publicacionEncontrada();
        tramosLibres(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-06-11"), libre("2030-06-12", "2030-07-01")));

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null)))
                .isInstanceOf(CotizacionNoDisponibleException.class);
    }

    @Test
    @DisplayName("Un calendario que cubre solo parte de la estadía impide cotizar")
    void cotizar_conCoberturaParcial_lanzaCotizacionNoDisponible() {
        publicacionEncontrada();
        tramosLibres(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-06-12")));

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null)))
                .isInstanceOf(CotizacionNoDisponibleException.class);
    }

    @Test
    @DisplayName("Los datos inválidos se rechazan antes que la falta de disponibilidad")
    void cotizar_conServicioInvalidoYSinCalendario_ganaElErrorDeDatos() {
        publicacionEncontrada();
        when(servicioAdicionalRepository.findByIdIdPublicacionAndIdNombreIn(12L, List.of("Spa")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, List.of("Spa"))))
                .isInstanceOf(DatosReservaInvalidosException.class);

        verifyNoInteractions(periodoRepository);
    }

    @Test
    @DisplayName("Sin la configuración de la plataforma falla con un error de estado")
    void cotizar_sinConfiguracion_lanzaIllegalState() {
        publicacionEncontrada();
        tramosLibres(LLEGADA, SALIDA, List.of(libre("2030-06-01", "2030-07-01")));
        when(configuracionRepository.findById(ConfiguracionPlataforma.ID_UNICO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cotizar(solicitud(LLEGADA, SALIDA, 2, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ apoyo

    private static CotizarEstanciaRequest solicitud(LocalDate llegada, LocalDate salida, Integer huespedes,
                                                    List<String> servicios) {
        return new CotizarEstanciaRequest(12L, llegada, salida, huespedes, servicios);
    }

    private void publicacionEncontrada() {
        when(publicacionRepository.findByIdPublicacionAndEstado(12L, EstadoPublicacion.ACTIVA))
                .thenReturn(Optional.of(publicacion));
    }

    private void tramosLibres(LocalDate llegada, LocalDate salida, List<PeriodoDisponibilidad> libres) {
        when(periodoRepository.buscarQueCruzan(12L, TipoPeriodo.DISPONIBLE, llegada, salida)).thenReturn(libres);
    }

    /** Todo lo que la cotización consulta cuando todo está bien. */
    private void prepararCotizacion(LocalDate llegada, LocalDate salida, List<PeriodoDisponibilidad> libres) {
        publicacionEncontrada();
        tramosLibres(llegada, salida, libres);
        when(configuracionRepository.findById(ConfiguracionPlataforma.ID_UNICO))
                .thenReturn(Optional.of(configuracion));
    }

    private static ServicioAdicional desayuno() {
        return new ServicioAdicional(12L, "Desayuno", new BigDecimal("20000.00"), TipoCobro.POR_NOCHE);
    }

    private static ServicioAdicional traslado() {
        return new ServicioAdicional(12L, "Traslado al aeropuerto", new BigDecimal("80000.00"),
                TipoCobro.POR_ESTANCIA);
    }

    private Publicacion publicacionActiva() {
        Usuario anfitrion = new Usuario(new NombreCompleto("Carlos", null, "Gómez", null), "carlos@correo.com",
                "hash", Rol.ANFITRION);
        PoliticaCancelacion flexible = BeanUtils.instantiateClass(PoliticaCancelacion.class);
        ReflectionTestUtils.setField(flexible, "nombre", "Flexible");
        ReflectionTestUtils.setField(flexible, "horasAnticipacionMin", 24);
        ReflectionTestUtils.setField(flexible, "porcentajeDevolucion", new BigDecimal("100.00"));
        DatosPublicacion datos = DatosPublicacion.normalizados("Apartamento frente al mar", "Dos habitaciones",
                "Calle 5 # 3-20", "Bocagrande", 4, new BigDecimal("250000.00"));
        Publicacion creada = new Publicacion(anfitrion, flexible, datos, new Ciudad("Cartagena"),
                new TipoAlojamiento("Apartamento"));
        ReflectionTestUtils.setField(creada, "idPublicacion", 12L);
        ReflectionTestUtils.setField(creada, "estado", EstadoPublicacion.ACTIVA);
        ReflectionTestUtils.setField(creada, "tarifaLimpieza", new BigDecimal("50000.00"));
        return creada;
    }

    private PeriodoDisponibilidad libre(String inicio, String fin) {
        return PeriodoDisponibilidad.disponible(publicacion, LocalDate.parse(inicio), LocalDate.parse(fin));
    }
}
