package com.inversioneslr.backend.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DesglosePrecioTest {

    private static final BigDecimal SIN_DESCUENTO = BigDecimal.ZERO;
    private static final BigDecimal DESCUENTO_10 = new BigDecimal("10.00");
    private static final BigDecimal SERVICIO_10 = new BigDecimal("10.00");

    @Test
    @DisplayName("Sin descuento: base, limpieza y tarifa de servicio suman el total")
    void calcular_sinDescuento_sumaBaseLimpiezaYServicio() {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"), 3,
                SIN_DESCUENTO, SERVICIO_10);

        assertThat(precio.precioBase()).isEqualByComparingTo("750000.00");
        assertThat(precio.porcentajeDescuento()).isEqualByComparingTo("0");
        assertThat(precio.valorDescuento()).isEqualByComparingTo("0.00");
        assertThat(precio.tarifaLimpieza()).isEqualByComparingTo("50000.00");
        assertThat(precio.porcentajeTarifaServicio()).isEqualByComparingTo("10.00");
        assertThat(precio.tarifaServicio()).isEqualByComparingTo("75000.00");
        assertThat(precio.total()).isEqualByComparingTo("875000.00");
    }

    @Test
    @DisplayName("Con descuento: la tarifa de servicio se calcula sobre la base menos el descuento")
    void calcular_conDescuento_servicioSobreLaBaseConDescuento() {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("250000.00"), new BigDecimal("50000.00"), 7,
                DESCUENTO_10, SERVICIO_10);

        assertThat(precio.precioBase()).isEqualByComparingTo("1750000.00");
        assertThat(precio.valorDescuento()).isEqualByComparingTo("175000.00");
        assertThat(precio.tarifaServicio()).isEqualByComparingTo("157500.00");
        assertThat(precio.total()).isEqualByComparingTo("1782500.00");
    }

    @Test
    @DisplayName("La tarifa de limpieza no entra en la base de la tarifa de servicio")
    void calcular_limpiezaNoCambiaLaTarifaDeServicio() {
        DesglosePrecio sinLimpieza = DesglosePrecio.calcular(new BigDecimal("100000.00"), BigDecimal.ZERO, 2,
                SIN_DESCUENTO, SERVICIO_10);
        DesglosePrecio conLimpieza = DesglosePrecio.calcular(new BigDecimal("100000.00"),
                new BigDecimal("80000.00"), 2, SIN_DESCUENTO, SERVICIO_10);

        assertThat(conLimpieza.tarifaServicio()).isEqualByComparingTo(sinLimpieza.tarifaServicio());
        assertThat(conLimpieza.total().subtract(sinLimpieza.total())).isEqualByComparingTo("80000.00");
    }

    @ParameterizedTest(name = "precio {0} x {1} noches con {2}% de descuento y {3}% de servicio -> servicio {4}")
    @CsvSource({
            // Medio centavo redondea hacia arriba: 100.05 * 10 % = 10.005 -> 10.01
            "100.05, 1, 0, 10, 10.01",
            // Menos de medio centavo redondea hacia abajo: 33333.33 * 10 % = 3333.333 -> 3333.33
            "33333.33, 1, 0, 10, 3333.33",
            // Un solo redondeo: 2000.99 * 0.50 % = 10.00495 -> 10.00 (redondeando antes a 4 decimales daría 10.01)
            "2000.99, 1, 0, 0.50, 10.00",
            // base 299999.97, descuento 30000.00, servicio 12.5 % de 269999.97 = 33749.99625 -> 33750.00
            "99999.99, 3, 10, 12.50, 33750.00"
    })
    @DisplayName("Los porcentajes se redondean a centavos una sola vez, hacia arriba en el punto medio")
    void calcular_redondeaAlCentavo(String precioNoche, long noches, String descuento, String servicio,
                                    String servicioEsperado) {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal(precioNoche), BigDecimal.ZERO, noches,
                new BigDecimal(descuento), new BigDecimal(servicio));

        assertThat(precio.tarifaServicio()).isEqualByComparingTo(servicioEsperado);
        assertThat(precio.tarifaServicio().scale()).isEqualTo(2);
        assertThat(precio.total().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("El total siempre es base menos descuento más limpieza más servicio")
    void calcular_totalCoincideConSusPartes() {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("187350.55"), new BigDecimal("35000.50"), 9,
                new BigDecimal("7.50"), new BigDecimal("12.25"));

        BigDecimal esperado = precio.precioBase().subtract(precio.valorDescuento())
                .add(precio.tarifaLimpieza()).add(precio.tarifaServicio());
        assertThat(precio.total()).isEqualByComparingTo(esperado);
    }

    @Test
    @DisplayName("Con porcentajes en cero solo se cobra la base y la limpieza")
    void calcular_sinPorcentajes_soloBaseYLimpieza() {
        DesglosePrecio precio = DesglosePrecio.calcular(new BigDecimal("90000.00"), new BigDecimal("10000.00"), 2,
                SIN_DESCUENTO, BigDecimal.ZERO);

        assertThat(precio.tarifaServicio()).isEqualByComparingTo("0.00");
        assertThat(precio.total()).isEqualByComparingTo("190000.00");
    }
}
