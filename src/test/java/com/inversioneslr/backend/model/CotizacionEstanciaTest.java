package com.inversioneslr.backend.model;

import com.inversioneslr.backend.model.CotizacionEstancia.ConceptoAdicional;
import com.inversioneslr.backend.model.enums.TipoCobro;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CotizacionEstanciaTest {

    private static final BigDecimal PRECIO_NOCHE = new BigDecimal("250000.00");
    private static final BigDecimal LIMPIEZA = new BigDecimal("50000.00");
    private static final BigDecimal DESCUENTO = new BigDecimal("10.00");
    private static final BigDecimal TARIFA_SERVICIO = new BigDecimal("10.00");

    private static final ServicioAdicional DESAYUNO = new ServicioAdicional(12L, "Desayuno",
            new BigDecimal("20000.00"), TipoCobro.POR_NOCHE);
    private static final ServicioAdicional TRASLADO = new ServicioAdicional(12L, "Traslado al aeropuerto",
            new BigDecimal("80000.00"), TipoCobro.POR_ESTANCIA);

    @Test
    void calcular_sinServicios_dejaElTotalDeLaEstadia() {
        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(PRECIO_NOCHE, LIMPIEZA, 3, BigDecimal.ZERO,
                TARIFA_SERVICIO, List.of());

        assertThat(cotizacion.noches()).isEqualTo(3);
        assertThat(cotizacion.precioNoche()).isEqualByComparingTo("250000.00");
        assertThat(cotizacion.desglose().precioBase()).isEqualByComparingTo("750000.00");
        assertThat(cotizacion.desglose().tarifaLimpieza()).isEqualByComparingTo("50000.00");
        assertThat(cotizacion.desglose().tarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(cotizacion.serviciosAdicionales()).isEmpty();
        assertThat(cotizacion.totalServiciosAdicionales()).isEqualByComparingTo("0");
        assertThat(cotizacion.total()).isEqualByComparingTo("875000.00");
    }

    @Test
    void calcular_conServicios_losSumaAlTotalSinCambiarLaTarifaDeServicio() {
        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(PRECIO_NOCHE, LIMPIEZA, 3, BigDecimal.ZERO,
                TARIFA_SERVICIO, List.of(DESAYUNO, TRASLADO));

        assertThat(cotizacion.serviciosAdicionales()).containsExactly(
                new ConceptoAdicional("Desayuno", TipoCobro.POR_NOCHE, new BigDecimal("20000.00"),
                        new BigDecimal("60000.00")),
                new ConceptoAdicional("Traslado al aeropuerto", TipoCobro.POR_ESTANCIA, new BigDecimal("80000.00"),
                        new BigDecimal("80000.00")));
        assertThat(cotizacion.totalServiciosAdicionales()).isEqualByComparingTo("140000.00");
        // La tarifa de servicio sigue siendo el 10 % de la base: los servicios no la pagan.
        assertThat(cotizacion.desglose().tarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(cotizacion.total()).isEqualByComparingTo("1015000.00");
    }

    @Test
    void calcular_conEstadiaLarga_elDescuentoNoAlcanzaALosServicios() {
        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(PRECIO_NOCHE, LIMPIEZA, 7, DESCUENTO,
                TARIFA_SERVICIO, List.of(DESAYUNO));

        assertThat(cotizacion.desglose().precioBase()).isEqualByComparingTo("1750000.00");
        assertThat(cotizacion.desglose().valorDescuento()).isEqualByComparingTo("175000.00");
        assertThat(cotizacion.desglose().tarifaServicio()).isEqualByComparingTo("157500.00");
        assertThat(cotizacion.serviciosAdicionales().get(0).valor()).isEqualByComparingTo("140000.00");
        // 1.750.000 - 175.000 + 50.000 + 157.500 = 1.782.500, más 140.000 del desayuno.
        assertThat(cotizacion.total()).isEqualByComparingTo("1922500.00");
    }

    @Test
    void calcular_laEstadiaCoincideConElDesgloseQueSeGuardaAlReservar() {
        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(PRECIO_NOCHE, LIMPIEZA, 7, DESCUENTO,
                TARIFA_SERVICIO, List.of(TRASLADO));

        assertThat(cotizacion.desglose())
                .isEqualTo(DesglosePrecio.calcular(PRECIO_NOCHE, LIMPIEZA, 7, DESCUENTO, TARIFA_SERVICIO));
        assertThat(cotizacion.total())
                .isEqualByComparingTo(cotizacion.desglose().total().add(new BigDecimal("80000.00")));
    }

    @Test
    void calcular_conservaElOrdenDeLosServicios() {
        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(PRECIO_NOCHE, LIMPIEZA, 2, BigDecimal.ZERO,
                TARIFA_SERVICIO, List.of(TRASLADO, DESAYUNO));

        assertThat(cotizacion.serviciosAdicionales())
                .extracting(ConceptoAdicional::nombre)
                .containsExactly("Traslado al aeropuerto", "Desayuno");
    }

    @Test
    void calcular_unServicioGratuito_apareceConValorCero() {
        ServicioAdicional cortesia = new ServicioAdicional(12L, "Estacionamiento", BigDecimal.ZERO,
                TipoCobro.POR_NOCHE);

        CotizacionEstancia cotizacion = CotizacionEstancia.calcular(PRECIO_NOCHE, LIMPIEZA, 3, BigDecimal.ZERO,
                TARIFA_SERVICIO, List.of(cortesia));

        assertThat(cotizacion.serviciosAdicionales()).hasSize(1);
        assertThat(cotizacion.serviciosAdicionales().get(0).valor()).isEqualByComparingTo("0");
        assertThat(cotizacion.total()).isEqualByComparingTo("875000.00");
    }
}
